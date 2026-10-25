package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvFileInspectorTest {

    private static final List<String> HEADER = List.of("id", "name");

    @TempDir
    Path temporaryDirectory;

    private final CsvFileInspector inspector = new CsvFileInspector();

    @ParameterizedTest
    @ValueSource(strings = {
            "id,name\n1,Alice\n2,Bob",
            "id,name\r\n1,Alice\r\n2,Bob\r\n",
            "id,name\n1,\"Smith,\nJohn \"\"Junior\"\"\"\n2,Mary\n"
    })
    void countsRecordsAcrossSupportedLayouts(String content)
            throws IOException {
        Path file = writeCsv(content);

        assertThat(inspector.countRecords(file, HEADER)).isEqualTo(2L);
    }

    @Test
    void acceptsHeaderOnlyFile() throws IOException {
        Path file = writeCsv("id,name\n");

        assertThat(inspector.countRecords(file, HEADER)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "name,id",
            "id",
            "id,name,extra",
            "id,id",
            "id, name",
            "\uFEFFid,name"
    })
    void rejectsUnexpectedHeader(String header) throws IOException {
        Path file = writeCsv(header + "\n");

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );

        assertThat(error.getMessage())
                .contains("header names or order do not match");
    }

    @Test
    void rejectsEmptyFile() throws IOException {
        Path file = writeCsv("");

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );

        assertThat(error.getMessage()).contains("no header");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1\n",
            "1,Alice,extra\n",
            "\n"
    })
    void rejectsIncorrectColumnCount(String row) throws IOException {
        Path file = writeCsv("id,name\n" + row);

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );

        assertThat(error.getMessage())
                .contains("data record 1")
                .contains("expected 2");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "id,name\n1,\"unfinished",
            "id,name\n1,\"Alice\"unexpected\n"
    })
    void reportsMalformedQuotedField(String content) throws IOException {
        Path file = writeCsv(content);

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );

        assertThat(error.getMessage()).contains("Failed to read or parse CSV");
        assertThat(error.getCause()).isNotNull();
    }

    @Test
    void rejectsInvalidUtf8() throws IOException {
        Path file = temporaryDirectory.resolve("invalid.csv");
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});

        assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );
    }

    @Test
    void reportsMissingFile() {
        Path file = temporaryDirectory.resolve("missing.csv");

        assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidExpectedHeaders")
    void rejectsInvalidExpectedHeader(List<String> expectedHeader)
            throws IOException {
        Path file = writeCsv("id,name\n");

        assertThrows(
                IllegalArgumentException.class,
                () -> inspector.countRecords(file, expectedHeader)
        );
    }

    static Stream<List<String>> invalidExpectedHeaders() {
        return Stream.of(
                List.<String>of(),
                List.of("id", " "),
                List.of("id", "id")
        );
    }

    @Test
    void reportsRecordNumberRatherThanPhysicalLineNumber()
            throws IOException {
        Path file = writeCsv(
                "id,name\n1,\"Alice\nSmith\"\n2\n"
        );

        IOException error = assertThrows(
                IOException.class,
                () -> inspector.countRecords(file, HEADER)
        );

        assertThat(error.getMessage()).contains("data record 2");
    }

    private Path writeCsv(String content) throws IOException {
        Path file = temporaryDirectory.resolve("input.csv");
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}