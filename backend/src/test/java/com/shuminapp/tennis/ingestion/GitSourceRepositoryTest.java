package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitSourceRepositoryTest {
    @TempDir Path temp;

    @Test void readsCommitInsteadOfDirtyWorktreeAndExportsExactBytes() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        fixture.write("atp/atp_players.csv", GitFixture.PLAYERS);
        fixture.write("README.md", "not selected");
        var identity = fixture.commit();
        fixture.write("atp/atp_players.csv", "dirty");
        fixture.write("wta/wta_players.csv", "untracked");
        var reader = new GitSourceRepository(fixture.root);
        var snapshot = reader.readSnapshot(identity);
        assertThat(snapshot.entries()).hasSize(1);
        assertThat(snapshot.excludedPaths()).containsExactly("README.md");
        Path exported = temp.resolve("export.csv");
        reader.exportBlob(snapshot.entries().getFirst().blobId(), exported);
        assertThat(Files.readString(exported)).isEqualTo(GitFixture.PLAYERS);
        assertThat(Files.readString(fixture.root.resolve("atp/atp_players.csv"))).isEqualTo("dirty");
        assertThrows(IOException.class, () -> reader.exportBlob(snapshot.entries().getFirst().blobId(), exported));
    }

    @Test void rejectsWrongOriginWithoutExposingIt() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        fixture.write("README.md", "test");
        var identity = fixture.commit();
        var wrong = new SourceSnapshotIdentity(URI.create("https://example.org/wrong"), identity.commitSha());
        assertThrows(IOException.class, () -> new GitSourceRepository(fixture.root).readSnapshot(wrong));
    }

    @Test void rejectsMissingCommitAndTreeObject() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        fixture.write("atp/atp_players.csv", GitFixture.PLAYERS);
        var identity = fixture.commit();
        var reader = new GitSourceRepository(fixture.root);
        assertThrows(IOException.class, () -> reader.readSnapshot(
                new SourceSnapshotIdentity(GitFixture.URL, "0".repeat(40))));
        String tree = fixture.git("rev-parse", "HEAD^{tree}").strip();
        assertThrows(IOException.class, () -> reader.readSnapshot(new SourceSnapshotIdentity(GitFixture.URL, tree)));
        assertThat(reader.readSnapshot(identity).entries()).hasSize(1);
    }

    @Test void rejectsSelectedSymlink() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        Files.createDirectories(fixture.root.resolve("atp"));
        Files.createSymbolicLink(fixture.root.resolve("atp/atp_players.csv"), Path.of("../outside.csv"));
        var identity = fixture.commit();
        assertThrows(IOException.class, () -> new GitSourceRepository(fixture.root).readSnapshot(identity));
    }

    @Test void excludesKnownDatasetsButRejectsNewRankingNames() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        fixture.write("atp/atp_players.csv", GitFixture.PLAYERS);
        fixture.write("atp/atp_matches_amateur.csv", "not selected");
        fixture.write("atp/atp_matches_qual_chall_2024.csv", "not selected");
        var identity = fixture.commit();
        assertThat(new GitSourceRepository(fixture.root).readSnapshot(identity).excludedPaths()).hasSize(2);
        fixture.write("atp/atp_rankings_30s.csv", "new candidate");
        var updated = fixture.commit();
        assertThrows(IOException.class, () -> new GitSourceRepository(fixture.root).readSnapshot(updated));
    }

    @Test void acceptsDotGitOriginAndPreservesOlderCommit() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        fixture.git("remote", "set-url", "origin", GitFixture.URL + ".git");
        fixture.write("atp/atp_players.csv", GitFixture.PLAYERS);
        var old = fixture.commit();
        fixture.write("wta/wta_players.csv", GitFixture.PLAYERS);
        fixture.commit();
        assertThat(new GitSourceRepository(fixture.root).readSnapshot(old).entries()).hasSize(1);
    }
}
