package com.shuminapp.tennis.ingestion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFilePlanTest {

    private static final Path ATP_PLAYERS =
            Path.of("atp/atp_players.csv");

    private static final Path WTA_PLAYERS =
            Path.of("wta/wta_players.csv");

    @Test
    void sortsPathsRegardlessOfInputOrder() {
        var first = new SourceFilePlan(List.of(WTA_PLAYERS, ATP_PLAYERS));
        var second = new SourceFilePlan(List.of(ATP_PLAYERS, WTA_PLAYERS));

        assertThat(first.relativePaths())
                .containsExactly(ATP_PLAYERS, WTA_PLAYERS);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void copiesInputListWithoutChangingIt() {
        var input = new ArrayList<>(List.of(WTA_PLAYERS, ATP_PLAYERS));

        var plan = new SourceFilePlan(input);

        assertThat(input).containsExactly(WTA_PLAYERS, ATP_PLAYERS);

        input.clear();

        assertThat(plan.relativePaths())
                .containsExactly(ATP_PLAYERS, WTA_PLAYERS);
    }

    @Test
    void preventsModificationOfReturnedList() {
        var plan = new SourceFilePlan(List.of(ATP_PLAYERS));

        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.relativePaths().add(WTA_PLAYERS)
        );
    }

    @Test
    void rejectsEmptyPlan() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new SourceFilePlan(List.of())
        );

        assertThat(error.getMessage()).contains("must not be empty");
    }

    @Test
    void rejectsDuplicatePaths() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new SourceFilePlan(
                        List.of(ATP_PLAYERS, WTA_PLAYERS, ATP_PLAYERS)
                )
        );

        assertThat(error.getMessage())
                .contains("Duplicate source path")
                .contains("atp/atp_players.csv");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "atp/atp_matches_qual_chall_2024.csv",
            "atp/./atp_players.csv",
            "atp/../atp/atp_players.csv"
    })
    void rejectsUnsupportedPathWithinPlan(String invalidPath) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceFilePlan(
                        List.of(WTA_PLAYERS, Path.of(invalidPath))
                )
        );
    }

    @Test
    void rejectsNullList() {
        assertThrows(
                NullPointerException.class,
                () -> new SourceFilePlan(null)
        );
    }

    @Test
    void rejectsNullElement() {
        var input = new ArrayList<Path>();
        input.add(ATP_PLAYERS);
        input.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new SourceFilePlan(input)
        );
    }
}