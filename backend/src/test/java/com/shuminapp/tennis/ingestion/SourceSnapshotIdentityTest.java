package com.shuminapp.tennis.ingestion;

import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceSnapshotIdentityTest {

    private static final URI REPOSITORY = URI.create(
            "https://github.com/Aneeshers/tennis-sackmann-archive"
    );

    private static final String COMMIT =
            "83733587353df8a41f2fd4f516147d5aa83f5a8d";

    @Test
    void preservesValidIdentity() {
        var identity = new SourceSnapshotIdentity(REPOSITORY, COMMIT);

        assertThat(identity.repositoryUrl()).isEqualTo(REPOSITORY);
        assertThat(identity.commitSha()).isEqualTo(COMMIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://github.com/example/repository",
            "ssh://github.com/example/repository",
            "example/repository",
            "https:/example/repository",
            "https://github.com",
            "https://github.com/",
            "https://user@github.com/example/repository",
            "https://github.com/example/repository?ref=main",
            "https://github.com/example/repository#readme"
    })
    void rejectsUnsupportedRepositoryUrl(String value) {
        URI repository = URI.create(value);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSnapshotIdentity(repository, COMMIT)
        );

        assertThat(error.getMessage()).contains("Repository URL must be");
    }

    @ParameterizedTest
    @MethodSource("invalidCommitValues")
    void rejectsInvalidCommitSha(String commitSha) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSnapshotIdentity(REPOSITORY, commitSha)
        );

        assertThat(error.getMessage())
                .contains("40 lowercase hexadecimal characters");
    }

    static Stream<String> invalidCommitValues() {
        return Stream.of(
                "",
                "main",
                "8373358",
                "a".repeat(39),
                "a".repeat(41),
                "A".repeat(40),
                "g".repeat(40),
                " " + COMMIT
        );
    }

    @Test
    void rejectsNullRepositoryUrl() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new SourceSnapshotIdentity(null, COMMIT)
        );

        assertThat(error.getMessage()).isEqualTo("repositoryUrl");
    }

    @Test
    void rejectsNullCommitSha() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> new SourceSnapshotIdentity(REPOSITORY, null)
        );

        assertThat(error.getMessage()).isEqualTo("commitSha");
    }
}