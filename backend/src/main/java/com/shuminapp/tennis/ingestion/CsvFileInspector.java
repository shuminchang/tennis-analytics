package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public final class CsvFileInspector {

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(false)
            .setIgnoreSurroundingSpaces(false)
            .setTrim(false)
            .setLenientEof(false)
            .setTrailingData(false)
            .get();

    public long countRecords(Path path, List<String> expectedHeader)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(expectedHeader, "expectedHeader");

        List<String> header = List.copyOf(expectedHeader);
        validateExpectedHeader(header);

        try (Reader reader = Files.newBufferedReader(
                    path, StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {

            Iterator<CSVRecord> records = parser.iterator();

            if (!records.hasNext()) {
                throw new IOException("CSV file has no header: " + path);
            }

            CSVRecord actualHeader = records.next();

            if (!actualHeader.toList().equals(header)) {
                throw new IOException(
                        "CSV header names or order do not match: " + path
                );
            }

            long count = 0;

            while (records.hasNext()) {
                CSVRecord record = records.next();

                if (record.size() != header.size()) {
                    throw new IOException(
                            "CSV data record " + (count + 1)
                                    + " has " + record.size()
                                    + " columns; expected " + header.size()
                                    + ": " + path
                    );
                }

                count++;
            }

            return count;
        } catch (UncheckedIOException exception) {
            throw new IOException(
                    "Failed to read or parse CSV: " + path,
                    exception
            );
        }
    }

    private void validateExpectedHeader(List<String> header) {
        if (header.isEmpty()
                || header.stream().anyMatch(String::isBlank)
                || new HashSet<>(header).size() != header.size()) {
            throw new IllegalArgumentException(
                    "Expected header must contain unique, nonblank names"
            );
        }
    }
}