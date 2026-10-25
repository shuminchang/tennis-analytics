package com.shuminapp.tennis.ingestion;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFileTypeTest {

    @ParameterizedTest
    @CsvSource({
            "atp/atp_players.csv, ATP_PLAYERS, ATP, PLAYERS",
            "wta/wta_players.csv, WTA_PLAYERS, WTA, PLAYERS",
            "atp/atp_matches_2024.csv, ATP_MATCHES, ATP, MATCHES",
            "wta/wta_matches_1968.csv, WTA_MATCHES, WTA, MATCHES",
            "atp/atp_rankings_current.csv, ATP_RANKINGS, ATP, RANKINGS",
            "wta/wta_rankings_current.csv, WTA_RANKINGS, WTA, RANKINGS"
    })
    void identifiesSupportedSource(
            String path,
            SourceFileType expectedType,
            String expectedTour,
            String expectedKind
    ) {
        SourceFileType result = SourceFileType.fromPath(Path.of(path));

        assertThat(result).isEqualTo(expectedType);
        assertThat(result.tour()).isEqualTo(expectedTour);
        assertThat(result.dataKind()).isEqualTo(expectedKind);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "atp_players.csv",
            "atp/atp_players.csv.bak",
            "ATP/atp_players.csv",
            "atp/wta_players.csv",
            "atp/atp_matches_qual_chall_2024.csv",
            "atp/atp_matches_futures_2024.csv",
            "wta/wta_matches_doubles_2024.csv",
            "atp/atp_matches_24.csv",
            "wta/wta_rankings_70s.csv",
            "atp/../atp/atp_players.csv",
            "atp/./atp_players.csv"
    })
    void rejectsUnsupportedPath(String path) {
        assertThrows(
                IllegalArgumentException.class,
                () -> SourceFileType.fromPath(Path.of(path))
        );
    }

    @Test
    void rejectsAbsolutePath() {
        Path path = Path.of("atp/atp_players.csv").toAbsolutePath();

        assertThrows(
                IllegalArgumentException.class,
                () -> SourceFileType.fromPath(path)
        );
    }

    @Test
    void acceptsConfirmedRankingDecades() {
        for (String decade : List.of("70s", "80s", "90s", "00s", "10s", "20s")) {
            Path path = Path.of("atp", "atp_rankings_" + decade + ".csv");

            assertThat(SourceFileType.fromPath(path))
                    .isEqualTo(SourceFileType.ATP_RANKINGS);
        }

        for (String decade : List.of("80s", "90s", "00s", "10s", "20s")) {
            Path path = Path.of("wta", "wta_rankings_" + decade + ".csv");

            assertThat(SourceFileType.fromPath(path))
                    .isEqualTo(SourceFileType.WTA_RANKINGS);
        }
    }

    @Test
    void providesPlayerHeadersInSourceOrder() {
        List<String> expected = List.of(
                "player_id", "name_first", "name_last", "hand",
                "dob", "ioc", "height", "wikidata_id"
        );

        assertThat(SourceFileType.ATP_PLAYERS.expectedHeader())
                .containsExactlyElementsOf(expected);
        assertThat(SourceFileType.WTA_PLAYERS.expectedHeader())
                .containsExactlyElementsOf(expected);
    }

    @Test
    void distinguishesAtpAndWtaRankingHeaders() {
        assertThat(SourceFileType.ATP_RANKINGS.expectedHeader())
                .containsExactly("ranking_date", "rank", "player", "points");

        assertThat(SourceFileType.WTA_RANKINGS.expectedHeader())
                .containsExactly(
                        "ranking_date", "rank", "player", "points", "tours"
                );
    }

    @Test
    void providesMatchHeadersInSourceOrder() {
        List<String> expected = List.of(
                "tourney_id", "tourney_name", "surface", "draw_size",
                "tourney_level", "tourney_date", "match_num",
                "winner_id", "winner_seed", "winner_entry", "winner_name",
                "winner_hand", "winner_ht", "winner_ioc", "winner_age",
                "loser_id", "loser_seed", "loser_entry", "loser_name",
                "loser_hand", "loser_ht", "loser_ioc", "loser_age",
                "score", "best_of", "round", "minutes",
                "w_ace", "w_df", "w_svpt", "w_1stIn", "w_1stWon",
                "w_2ndWon", "w_SvGms", "w_bpSaved", "w_bpFaced",
                "l_ace", "l_df", "l_svpt", "l_1stIn", "l_1stWon",
                "l_2ndWon", "l_SvGms", "l_bpSaved", "l_bpFaced",
                "winner_rank", "winner_rank_points",
                "loser_rank", "loser_rank_points"
        );

        assertThat(SourceFileType.ATP_MATCHES.expectedHeader())
                .containsExactlyElementsOf(expected);
        assertThat(SourceFileType.WTA_MATCHES.expectedHeader())
                .containsExactlyElementsOf(expected);
    }

    @Test
    void preventsModificationOfSharedHeaders() {
        for (SourceFileType type : SourceFileType.values()) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> type.expectedHeader().add("unexpected")
            );
        }
    }
}