package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourcePathResolverTest {

    @TempDir
    Path temporaryDirectory;

    private final SourcePathResolver resolver = new SourcePathResolver();

    private Path root;
    private Path source;

    @BeforeEach
    void createFiles() throws IOException {
        root = Files.createDirectory(
                temporaryDirectory.resolve("snapshot")
        );
        Path atp = Files.createDirectory(root.resolve("atp"));
        source = Files.writeString(atp.resolve("players.csv"), "player_id\n");
    }

    @Test
    void resolvesFileInsideSnapshot() throws IOException {
        Path result = resolver.resolve(root, Path.of("atp/players.csv"));

        assertThat(result).isEqualTo(source.toRealPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsBlankPath(String path) {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(root, Path.of(path))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../outside.csv",
            "atp/../players.csv"
    })
    void rejectsParentDirectoryComponents(String path) {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(root, Path.of(path))
        );
    }

    @Test
    void rejectsAbsolutePathEvenInsideSnapshot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(root, source.toAbsolutePath())
        );
    }

    @Test
    void reportsMissingFile() {
        assertThrows(
                IOException.class,
                () -> resolver.resolve(root, Path.of("missing.csv"))
        );
    }

    @Test
    void rejectsDirectoryAsSource() {
        assertThrows(
                IOException.class,
                () -> resolver.resolve(root, Path.of("atp"))
        );
    }

    @Test
    void rejectsFileAsSnapshotRoot() {
        assertThrows(
                IOException.class,
                () -> resolver.resolve(source, Path.of("players.csv"))
        );
    }

    @Test
    void acceptsSymbolicLinkToFileInsideSnapshot() throws IOException {
        Files.createSymbolicLink(
                root.resolve("players-link.csv"),
                Path.of("atp/players.csv")
        );

        Path result = resolver.resolve(root, Path.of("players-link.csv"));

        assertThat(result).isEqualTo(source.toRealPath());
    }

    @Test
    void rejectsLinkToSiblingWithSimilarDirectoryName() throws IOException {
        Path outside = Files.createDirectory(
                temporaryDirectory.resolve("snapshot-other")
        );
        Path outsideFile = Files.writeString(
                outside.resolve("players.csv"),
                "outside"
        );
        Files.createSymbolicLink(
                root.resolve("outside-link.csv"),
                outsideFile.toAbsolutePath()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(root, Path.of("outside-link.csv"))
        );
    }

    @Test
    void rejectsFileReachedThroughExternalDirectoryLink() throws IOException {
        Path outside = Files.createDirectory(
                temporaryDirectory.resolve("external")
        );
        Files.writeString(outside.resolve("players.csv"), "outside");
        Files.createSymbolicLink(
                root.resolve("linked-directory"),
                outside.toAbsolutePath()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        root,
                        Path.of("linked-directory/players.csv")
                )
        );
    }
}