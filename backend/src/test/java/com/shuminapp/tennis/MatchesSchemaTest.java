package com.shuminapp.tennis;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
@Transactional
class MatchesSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createPlayers() {
        jdbcTemplate.update(
                """
                INSERT INTO players (tour, player_id)
                VALUES ('ATP', 100001),
                       ('ATP', 100002),
                       ('WTA', 100001),
                       ('WTA', 100002),
                       ('WTA', 100003)
                """
        );
    }

    @Test
    void acceptsDifferentSourceKeysAndGeneratesIds() {
        Long first = insertMatch("ATP", "test-event", "R32", 1);
        Long second = insertMatch("ATP", "test-event", "R16", 1);
        Long third = insertMatch("WTA", "test-event", "R32", 1);
        Long fourth = insertMatch("ATP", "other-event", "R32", 1);
        Long fifth = insertMatch("ATP", "test-event", "R32", 2);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(third).isNotNull();
        assertThat(fourth).isNotNull();
        assertThat(fifth).isNotNull();

        assertThat(java.util.List.of(first, second, third, fourth, fifth))
                .doesNotHaveDuplicates();

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matches",
                Long.class
        );
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void rejectsDuplicateSourceKeyEvenWhenWinnerChanges() {
        insertMatch("ATP", "test-event", "R32", 1);

        assertViolation(
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO matches (
                            tour, tourney_id, tourney_date,
                            round, match_num, winner_id, loser_id
                        )
                        VALUES (
                            'ATP', 'test-event', DATE '2026-01-05',
                            'R32', 1, 100002, 100001
                        )
                        """
                ),
                "23505",
                "uq_matches_source"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "100003, 100002, fk_matches_winner",
            "999999, 100002, fk_matches_winner",
            "100001, 100003, fk_matches_loser",
            "100001, 999999, fk_matches_loser"
    })
    void rejectsPlayersMissingFromTheSameTour(
            int winnerId,
            int loserId,
            String constraint
    ) {
        assertViolation(
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO matches (
                            tour, tourney_id, tourney_date,
                            round, match_num, winner_id, loser_id
                        )
                        VALUES (
                            'ATP', 'test-event', DATE '2026-01-05',
                            'R32', 1, ?, ?
                        )
                        """,
                        winnerId, loserId
                ),
                "23503",
                constraint
        );
    }

    @Test
    void preservesSourceValuesThatRequireQualityReview() {
        Long id = insertMatch("ATP", "test-event", "ER", 0);

        jdbcTemplate.update(
                """
                UPDATE matches
                SET winner_seed = '1F',
                    loser_seed = 'WC',
                    tourney_level = '50+H',
                    winner_age = -1.5,
                    winner_height_cm = 3,
                    loser_id = winner_id,
                    l_bp_saved = -4,
                    w_svpt = 10,
                    w_1st_in = 11,
                    minutes = 0,
                    score = '7-Oct'
                WHERE id = ?
                """,
                id
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT winner_seed, loser_seed, tourney_level,
                       winner_age, winner_height_cm,
                       winner_id, loser_id, l_bp_saved,
                       w_svpt, w_1st_in, minutes, score
                FROM matches
                WHERE id = ?
                """,
                id
        );

        assertThat(row.get("winner_seed")).isEqualTo("1F");
        assertThat(row.get("loser_seed")).isEqualTo("WC");
        assertThat(row.get("tourney_level")).isEqualTo("50+H");
        assertThat((java.math.BigDecimal) row.get("winner_age"))
                .isEqualByComparingTo("-1.5");
        assertThat(((Number) row.get("winner_height_cm")).intValue())
                .isEqualTo(3);
        assertThat(row.get("winner_id")).isEqualTo(row.get("loser_id"));
        assertThat(((Number) row.get("l_bp_saved")).intValue()).isEqualTo(-4);
        assertThat(((Number) row.get("w_svpt")).intValue()).isEqualTo(10);
        assertThat(((Number) row.get("w_1st_in")).intValue()).isEqualTo(11);
        assertThat(((Number) row.get("minutes")).intValue()).isZero();
        assertThat(row.get("score")).isEqualTo("7-Oct");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void acceptsSupportedBestOfValues(int bestOf) {
        Long id = insertMatch("ATP", "test-event", "R32", 1);

        jdbcTemplate.update(
                "UPDATE matches SET best_of = ? WHERE id = ?",
                bestOf, id
        );

        Integer actual = jdbcTemplate.queryForObject(
                "SELECT best_of FROM matches WHERE id = ?",
                Integer.class,
                id
        );
        assertThat(actual).isEqualTo(bestOf);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "tour = 'XYZ'           | ck_matches_tour",
            "tourney_id = ''        | ck_matches_tourney_id_not_blank",
            "round = '   '          | ck_matches_round_not_blank",
            "winner_id = 0          | ck_matches_winner_id_positive",
            "loser_id = 0           | ck_matches_loser_id_positive",
            "draw_size = 0          | ck_matches_draw_size_positive",
            "winner_rank = 0        | ck_matches_winner_rank_positive",
            "loser_rank = 0         | ck_matches_loser_rank_positive",
            "winner_rank_points = -1 | ck_matches_winner_points_nonnegative",
            "loser_rank_points = -1 | ck_matches_loser_points_nonnegative",
            "winner_height_cm = 0   | ck_matches_winner_height_positive",
            "loser_height_cm = 0    | ck_matches_loser_height_positive",
            "best_of = 2            | ck_matches_best_of",
            "minutes = -1           | ck_matches_minutes_nonnegative"
    })
    void rejectsInvalidValues(String assignment, String constraint) {
        Long id = insertMatch("ATP", "test-event", "R32", 1);

        // assignment 僅來自上方固定測試資料，不接受外部輸入。
        assertViolation(
                () -> jdbcTemplate.update(
                        "UPDATE matches SET " + assignment + " WHERE id = ?",
                        id
                ),
                "23514",
                constraint
        );
    }

    private Long insertMatch(
            String tour,
            String eventId,
            String round,
            int matchNumber
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO matches (
                    tour, tourney_id, tourney_date,
                    round, match_num, winner_id, loser_id
                )
                VALUES (?, ?, DATE '2026-01-05', ?, ?, 100001, 100002)
                RETURNING id
                """,
                Long.class,
                tour, eventId, round, matchNumber
        );
    }

    private void assertViolation(
            Runnable operation,
            String expectedSqlState,
            String expectedConstraint
    ) {
        DataIntegrityViolationException error = assertThrows(
                DataIntegrityViolationException.class,
                operation::run
        );

        Throwable cause = error.getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);

        SQLException sqlError = (SQLException) cause;
        assertThat(sqlError.getSQLState()).isEqualTo(expectedSqlState);
        assertThat(sqlError.getMessage()).contains(expectedConstraint);
    }
}