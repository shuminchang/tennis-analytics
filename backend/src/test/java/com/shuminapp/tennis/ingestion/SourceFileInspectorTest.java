package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFileInspectorTest {

    private static final Path RANKINGS =
            Path.of("atp/atp_rankings_current.csv");

    private static final String RANKING_HEADER =
            "ranking_date,rank,player,points\n";

    private static final String RANKING_CONTENT =
            RANKING_HEADER + "20260105,1,100001,100\n";

    private static final String RANKING_SHA256 =
            "ec8cbd5cb820c7ed90fa73107a72b194bb67659046947942fb93720bc3507a3a";

    @TempDir
    Path root;

    private final SourceFileInspector inspector = new SourceFileInspector();

    @Test
    void returnsCompleteTypedResult() throws IOException {
        writeCsv(RANKINGS, RANKING_CONTENT);

        var result = inspector.inspect(root, RANKINGS);

        assertThat(result.relativePath()).isEqualTo(RANKINGS);
        assertThat(result.type()).isEqualTo(SourceFileType.ATP_RANKINGS);
        assertThat(result.type().tour()).isEqualTo("ATP");
        assertThat(result.type().dataKind()).isEqualTo("RANKINGS");
        assertThat(result.sha256()).isEqualTo(RANKING_SHA256);
        assertThat(result.byteSize()).isEqualTo(54L);
        assertThat(result.recordCount()).isEqualTo(1L);
    }

    @ParameterizedTest
    @CsvSource({
            "atp/atp_players.csv, ATP_PLAYERS",
            "wta/wta_players.csv, WTA_PLAYERS",
            "atp/atp_matches_2024.csv, ATP_MATCHES",
            "wta/wta_matches_2024.csv, WTA_MATCHES",
            "atp/atp_rankings_current.csv, ATP_RANKINGS",
            "wta/wta_rankings_current.csv, WTA_RANKINGS"
    })
    void acceptsHeaderOnlyFileForEachType(
            String filename,
            SourceFileType expectedType
    ) throws IOException {
        Path path = Path.of(filename);
        writeCsv(path, String.join(",", expectedType.expectedHeader()) + "\n");

        var result = inspector.inspect(root, path);

        assertThat(result.type()).isEqualTo(expectedType);
        assertThat(result.relativePath()).isEqualTo(path);
        assertThat(result.recordCount()).isZero();
    }

    @Test
    void measuresUtf8BytesAndCountsMultilineRecord() throws IOException {
        Path path = Path.of("atp/atp_players.csv");
        writeCsv(
                path,
                "player_id,name_first,name_last,hand,dob,ioc,height,wikidata_id\n"
                        + "100001,\"王,\n小明\",Test,R,20000101,TPE,180,Q1\n"
        );

        var result = inspector.inspect(root, path);

        assertThat(result.type()).isEqualTo(SourceFileType.ATP_PLAYERS);
        assertThat(result.sha256()).isEqualTo(
                "c148ff51c71f169b2c7991a5083cec41657e88bc6aa5d65ff0cc8665f4a5cd09"
        );
        assertThat(result.byteSize()).isEqualTo(111L);
        assertThat(result.recordCount()).isEqualTo(1L);
    }

    @Test
    void returnsSameResultForUnchangedFile() throws IOException {
        writeCsv(RANKINGS, RANKING_CONTENT);

        var first = inspector.inspect(root, RANKINGS);
        var second = inspector.inspect(root, RANKINGS);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void preservesLogicalPathOfInternalSymbolicLink() throws IOException {
        Path target = writeCsv(Path.of("stored/rankings.csv"), RANKING_CONTENT);
        Files.createDirectories(root.resolve("atp"));
        Files.createSymbolicLink(root.resolve(RANKINGS), target.toAbsolutePath());

        var result = inspector.inspect(root, RANKINGS);

        assertThat(result.relativePath()).isEqualTo(RANKINGS);
        assertThat(result.type()).isEqualTo(SourceFileType.ATP_RANKINGS);
        assertThat(result.sha256()).isEqualTo(RANKING_SHA256);
    }

    @Test
    void rejectsSymbolicLinkOutsideSnapshot() throws IOException {
        Path snapshot = Files.createDirectory(root.resolve("snapshot"));
        Path outside = writeCsv(Path.of("outside.csv"), RANKING_CONTENT);
        Files.createDirectories(snapshot.resolve("atp"));
        Files.createSymbolicLink(
                snapshot.resolve(RANKINGS),
                outside.toAbsolutePath()
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> inspector.inspect(snapshot, RANKINGS)
        );

        assertThat(error.getMessage()).contains("outside the snapshot root");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "20260105,1\n",
            "20260105,1,100001,\"unfinished"
    })
    void propagatesCsvInspectionFailure(String row) throws IOException {
        writeCsv(RANKINGS, RANKING_HEADER + row);

        assertThrows(
                IOException.class,
                () -> inspector.inspect(root, RANKINGS)
        );
    }

    @Test
    void reportsMissingSupportedFile() {
        assertThrows(
                IOException.class,
                () -> inspector.inspect(root, RANKINGS)
        );
    }

    @Test
    void rejectsAtpHeaderInWtaRankingFile() throws IOException {
        Path path = Path.of("wta/wta_rankings_current.csv");
        writeCsv(path, RANKING_CONTENT);

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.inspect(root, path)
        );

        assertThat(error.getMessage())
                .contains("header names or order do not match");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "atp/atp_matches_qual_chall_2024.csv",
            "atp/./atp_rankings_current.csv",
            "atp/wta_rankings_current.csv"
    })
    void rejectsUnsupportedSourcePath(String filename) {
        assertThrows(
                IllegalArgumentException.class,
                () -> inspector.inspect(root, Path.of(filename))
        );
    }

    private Path writeCsv(Path relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}