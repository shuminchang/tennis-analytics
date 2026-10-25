package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFileHasherTest {

    @TempDir
    Path temporaryDirectory;

    private final SourceFileHasher hasher = new SourceFileHasher();

    @Test
    void hashesEmptyFile() throws IOException {
        Path file = Files.createFile(
                temporaryDirectory.resolve("empty.csv")
        );

        assertThat(hasher.sha256(file)).isEqualTo(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
    }

    @Test
    void hashesKnownText() throws IOException {
        Path file = temporaryDirectory.resolve("abc.csv");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        assertThat(hasher.sha256(file)).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    @Test
    void hashesFileLargerThanBuffer() throws IOException {
        byte[] content = new byte[1_000_000];
        Arrays.fill(content, (byte) 'a');

        Path file = temporaryDirectory.resolve("large.csv");
        Files.write(file, content);

        assertThat(hasher.sha256(file)).isEqualTo(
                "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"
        );
    }

    @Test
    void reportsMissingFile() {
        Path missingFile = temporaryDirectory.resolve("missing.csv");

        assertThrows(
                IOException.class,
                () -> hasher.sha256(missingFile)
        );
    }
}