package com.shuminapp.tennis.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceSnapshotStoreTest {
    @TempDir Path temp;
    GitFixture fixture;
    SourceSnapshotIdentity identity;
    final SourceSnapshotStore store = new SourceSnapshotStore();
    Path archive;

    @BeforeEach void setup() throws Exception {
        fixture = new GitFixture(temp.resolve("repo"));
        fixture.write("atp/atp_players.csv", GitFixture.PLAYERS);
        fixture.write("LICENSE", "fixture-license\n");
        fixture.write("README.md", "fixture-readme\n");
        fixture.write("atp/atp_matches_amateur.csv", "excluded\n");
        identity = fixture.commit();
        archive = temp.resolve("archive");
    }

    @AfterEach void allowJUnitCleanup() throws IOException {
        try (var paths = Files.walk(temp)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
                }
            }
        }
    }

    @Test void preparesReadOnlyArchiveFromCommitAndVerifiesAfterRelocation() throws Exception {
        fixture.write("atp/atp_players.csv", "dirty worktree");
        var manifest = store.prepare(fixture.root, identity, archive);
        assertThat(manifest.files()).hasSize(1);
        assertThat(manifest.files().getFirst().recordCount()).isEqualTo(1);
        assertThat(manifest.supportingFiles()).hasSize(2);
        assertThat(Files.readString(archive.resolve("atp/atp_players.csv"))).isEqualTo(GitFixture.PLAYERS);
        assertThat(Files.isWritable(archive.resolve("atp/atp_players.csv"))).isFalse();
        assertThat(store.verify(fixture.root, identity, archive)).isEqualTo(manifest);
        Path restored = temp.resolve("restored");
        Files.createDirectory(restored);
        try (var paths = Files.walk(archive)) {
            for (Path source : paths.sorted().toList()) {
                Path destination = restored.resolve(archive.relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(destination);
                else Files.copy(source, destination);
            }
        }
        assertThat(store.verify(fixture.root, identity, restored)).isEqualTo(manifest);
    }

    @Test void neverOverwritesExistingDestination() throws Exception {
        Files.createDirectory(archive);
        Files.writeString(archive.resolve("mine"), "keep");
        assertThrows(IOException.class, () -> store.prepare(fixture.root, identity, archive));
        assertThat(Files.readString(archive.resolve("mine"))).isEqualTo("keep");
    }

    @Test void rejectsDestinationInsideOriginalRepository() {
        assertThrows(IOException.class, () -> store.prepare(fixture.root, identity, fixture.root.resolve("export")));
    }

    @Test void removesPrivateStagingWhenCsvValidationFails() throws Exception {
        fixture.write("wta/wta_players.csv", "wrong,header\n");
        var bad = fixture.commit();
        assertThrows(IOException.class, () -> store.prepare(fixture.root, bad, archive));
        assertThat(Files.exists(archive)).isFalse();
        try (var paths = Files.list(temp)) {
            assertThat(paths.map(p -> p.getFileName().toString())).noneMatch(n -> n.startsWith(".tennis-source-"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "extra", "symlink", "content", "recordCount", "identity", "path", "support"})
    void rejectsTamperedArchive(String change) throws Exception {
        store.prepare(fixture.root, identity, archive);
        allowJUnitCleanup();
        Path csv = archive.resolve("atp/atp_players.csv");
        switch (change) {
            case "missing" -> Files.delete(csv);
            case "extra" -> Files.writeString(archive.resolve("extra.csv"), "extra");
            case "symlink" -> {
                Files.delete(csv);
                Files.createSymbolicLink(csv, fixture.root.resolve("atp/atp_players.csv"));
            }
            case "content" -> {
                Files.setPosixFilePermissions(csv, PosixFilePermissions.fromString("rw-------"));
                Files.writeString(csv, GitFixture.PLAYERS.replace("First", "Other"));
            }
            case "support" -> {
                Path license = archive.resolve("LICENSE");
                Files.setPosixFilePermissions(license, PosixFilePermissions.fromString("rw-------"));
                Files.writeString(license, "changed license");
            }
            default -> {
                Path file = archive.resolve(SourceSnapshotStore.MANIFEST_NAME);
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                var mapper = new ObjectMapper();
                ObjectNode json = (ObjectNode) mapper.readTree(file.toFile());
                if (change.equals("recordCount")) ((ObjectNode) json.get("files").get(0)).put("recordCount", 99);
                if (change.equals("identity")) ((ObjectNode) json.get("identity")).put("commitSha", "0".repeat(40));
                if (change.equals("path")) ((ObjectNode) json.get("files").get(0)).put("relativePath", "../escape.csv");
                mapper.writeValue(file.toFile(), json);
            }
        }
        assertThrows(IOException.class, () -> store.verify(fixture.root, identity, archive));
    }
}
