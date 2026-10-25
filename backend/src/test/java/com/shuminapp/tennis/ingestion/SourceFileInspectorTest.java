package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFileInspectorTest {

    private static final List<String> HEADER = List.of("id", "name");
    private static final String CONTENT = "id,name\n1,Alice\n2,Bob\n";

    @TempDir
    Path root;

    private final SourceFileInspector inspector = new SourceFileInspector();

    @Test
    void returnsCompleteInspectionResult() throws IOException {
        writeCsv(CONTENT);

        var result = inspector.inspect(
                root, Path.of("atp/./players.csv"), HEADER
        );

        assertThat(result.relativePath())
                .isEqualTo(Path.of("atp/players.csv"));
        assertThat(result.sha256()).isEqualTo(
                "2e9f445b06e5ca0fb9638798078f6a239ea1fcace10e1dcfb70ceb3a3322ad44"
        );
        assertThat(result.byteSize()).isEqualTo(22L);
        assertThat(result.recordCount()).isEqualTo(2L);
    }

    @Test
    void measuresUtf8BytesAndCountsMultilineRecord() throws IOException {
        writeCsv("id,name\n1,\"王,\n小明\"\n");

        var result = inspector.inspect(
                root, Path.of("atp/players.csv"), HEADER
        );

        assertThat(result.sha256()).isEqualTo(
                "7ffd65f5bf5524b75b067f2643653bf01ba4319c22af93dfb463dc1ab08f2de3"
        );
        assertThat(result.byteSize()).isEqualTo(24L);
        assertThat(result.recordCount()).isEqualTo(1L);
    }

    @Test
    void acceptsHeaderOnlyFile() throws IOException {
        writeCsv("id,name\n");

        var result = inspector.inspect(
                root, Path.of("atp/players.csv"), HEADER
        );

        assertThat(result.sha256()).isEqualTo(
                "40d6bfdc74eae2ed68a97137ce414fa4ca6de1b3831cfd9a73c4622d8a8942c1"
        );
        assertThat(result.byteSize()).isEqualTo(8L);
        assertThat(result.recordCount()).isZero();
    }

    @Test
    void returnsSameResultForUnchangedFile() throws IOException {
        writeCsv(CONTENT);
        Path relativePath = Path.of("atp/players.csv");

        var first = inspector.inspect(root, relativePath, HEADER);
        var second = inspector.inspect(root, relativePath, HEADER);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void preservesLogicalPathOfInternalSymbolicLink() throws IOException {
        writeCsv(CONTENT);
        Files.createSymbolicLink(
                root.resolve("players-link.csv"),
                Path.of("atp/players.csv")
        );

        var result = inspector.inspect(
                root, Path.of("players-link.csv"), HEADER
        );

        assertThat(result.relativePath())
                .isEqualTo(Path.of("players-link.csv"));
        assertThat(result.sha256()).isEqualTo(
                "2e9f445b06e5ca0fb9638798078f6a239ea1fcace10e1dcfb70ceb3a3322ad44"
        );
        assertThat(result.recordCount()).isEqualTo(2L);
    }

    @Test
    void rejectsSymbolicLinkOutsideSnapshot() throws IOException {
        Path snapshot = Files.createDirectory(root.resolve("snapshot"));
        Path outside = Files.writeString(
                root.resolve("outside.csv"),
                CONTENT,
                StandardCharsets.UTF_8
        );
        Files.createSymbolicLink(
                snapshot.resolve("linked.csv"),
                outside.toAbsolutePath()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> inspector.inspect(
                        snapshot, Path.of("linked.csv"), HEADER
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "id,name\n1\n",
            "id,name\n1,\"unfinished"
    })
    void propagatesCsvInspectionFailure(String content) throws IOException {
        writeCsv(content);

        assertThrows(
                IOException.class,
                () -> inspector.inspect(
                        root, Path.of("atp/players.csv"), HEADER
                )
        );
    }

    @Test
    void reportsMissingFile() {
        assertThrows(
                IOException.class,
                () -> inspector.inspect(
                        root, Path.of("missing.csv"), HEADER
                )
        );
    }

    private void writeCsv(String content) throws IOException {
        Path directory = Files.createDirectories(root.resolve("atp"));
        Files.writeString(
                directory.resolve("players.csv"),
                content,
                StandardCharsets.UTF_8
        );
    }
}