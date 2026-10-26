package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFileBatchInspectorTest {

    private static final Path ATP =
            Path.of("atp/atp_rankings_current.csv");

    private static final Path WTA =
            Path.of("wta/wta_rankings_current.csv");

    private static final String ATP_CONTENT =
            "ranking_date,rank,player,points\n"
                    + "20260105,1,100001,100\n";

    private static final String WTA_CONTENT =
            "ranking_date,rank,player,points,tours\n"
                    + "20260105,1,200001,100,10\n"
                    + "20260105,2,200002,90,12\n";

    @TempDir
    Path root;

    private final SourceFileBatchInspector inspector =
            new SourceFileBatchInspector();

    @Test
    void returnsAllResultsInPlanOrder() throws IOException {
        writeCsv(ATP, ATP_CONTENT);
        writeCsv(WTA, WTA_CONTENT);
        var plan = new SourceFilePlan(List.of(WTA, ATP));

        var results = inspector.inspect(root, plan);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).relativePath()).isEqualTo(ATP);
        assertThat(results.get(0).type())
                .isEqualTo(SourceFileType.ATP_RANKINGS);
        assertThat(results.get(0).recordCount()).isEqualTo(1L);
        assertThat(results.get(0).byteSize()).isEqualTo(54L);
        assertThat(results.get(0).sha256()).isEqualTo(
                "ec8cbd5cb820c7ed90fa73107a72b194bb67659046947942fb93720bc3507a3a"
        );

        assertThat(results.get(1).relativePath()).isEqualTo(WTA);
        assertThat(results.get(1).type())
                .isEqualTo(SourceFileType.WTA_RANKINGS);
        assertThat(results.get(1).recordCount()).isEqualTo(2L);
    }

    @Test
    void returnsUnmodifiableResults() throws IOException {
        writeCsv(ATP, ATP_CONTENT);
        var plan = new SourceFilePlan(List.of(ATP));

        var results = inspector.inspect(root, plan);

        assertThrows(
                UnsupportedOperationException.class,
                () -> results.clear()
        );
    }

    @Test
    void failsWholeCallWhenLaterFileIsMissing() throws IOException {
        writeCsv(ATP, ATP_CONTENT);
        var plan = new SourceFilePlan(List.of(ATP, WTA));

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.inspect(root, plan)
        );

        assertThat(error.getMessage()).contains(WTA.toString());
        assertThat(error.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    void preservesCsvFailureFromLaterFile() throws IOException {
        writeCsv(ATP, ATP_CONTENT);
        writeCsv(WTA, "wrong,header\n");
        var plan = new SourceFilePlan(List.of(ATP, WTA));

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.inspect(root, plan)
        );

        assertThat(error.getMessage()).contains(WTA.toString());
        assertThat(error.getCause()).isInstanceOf(IOException.class);
        assertThat(error.getCause().getMessage())
                .contains("header names or order do not match");
    }

    @Test
    void preservesOutsideSnapshotFailure() throws IOException {
        Path snapshot = Files.createDirectory(root.resolve("snapshot"));
        Path outside = writeCsv(Path.of("outside.csv"), ATP_CONTENT);
        Files.createDirectories(snapshot.resolve("atp"));
        Files.createSymbolicLink(
                snapshot.resolve(ATP),
                outside.toAbsolutePath()
        );
        var plan = new SourceFilePlan(List.of(ATP));

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.inspect(snapshot, plan)
        );

        assertThat(error.getMessage()).contains(ATP.toString());
        assertThat(error.getCause())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(error.getCause().getMessage())
                .contains("outside the snapshot root");
    }

    @Test
    void keepsSeparateCallsIndependent() throws IOException {
        writeCsv(ATP, ATP_CONTENT);
        writeCsv(WTA, WTA_CONTENT);

        var first = inspector.inspect(
                root, new SourceFilePlan(List.of(ATP))
        );
        var second = inspector.inspect(
                root, new SourceFilePlan(List.of(WTA))
        );

        assertThat(first).hasSize(1);
        assertThat(first.get(0).relativePath()).isEqualTo(ATP);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).relativePath()).isEqualTo(WTA);
    }

    @Test
    void startsFreshAfterFailedAttempt() throws IOException {
        writeCsv(ATP, ATP_CONTENT);
        var plan = new SourceFilePlan(List.of(ATP, WTA));

        assertThrows(
                IOException.class,
                () -> inspector.inspect(root, plan)
        );

        writeCsv(WTA, WTA_CONTENT);

        var results = inspector.inspect(root, plan);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).relativePath()).isEqualTo(ATP);
        assertThat(results.get(1).relativePath()).isEqualTo(WTA);
    }

    private Path writeCsv(Path relativePath, String content)
            throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}