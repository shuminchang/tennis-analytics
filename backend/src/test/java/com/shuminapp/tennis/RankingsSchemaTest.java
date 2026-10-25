package com.shuminapp.tennis;

import java.sql.SQLException;
import java.time.LocalDate;

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
class RankingsSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createPlayers() {
        jdbcTemplate.update(
                """
                INSERT INTO players (tour, player_id)
                VALUES ('ATP', 100001),
                       ('ATP', 100002),
                       ('WTA', 100001)
                """
        );
    }

    @Test
    void acceptsTiesDifferentDatesAndToursWithOptionalValues() {
        insertRanking("ATP", "2026-01-05", 100001, 1, null, null);
        insertRanking("ATP", "2026-01-05", 100002, 1, 0, null);
        insertRanking("ATP", "2026-01-12", 100001, 2, 100, null);
        insertRanking("WTA", "2026-01-05", 100001, 1, 0, 0);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rankings",
                Long.class
        );
        assertThat(count).isEqualTo(4L);

        Integer missingPoints = jdbcTemplate.queryForObject(
                """
                SELECT points FROM rankings
                WHERE tour = 'ATP'
                  AND ranking_date = DATE '2026-01-05'
                  AND player_id = 100001
                """,
                Integer.class
        );
        assertThat(missingPoints).isNull();

        Integer zeroTours = jdbcTemplate.queryForObject(
                """
                SELECT source_tours FROM rankings
                WHERE tour = 'WTA'
                  AND ranking_date = DATE '2026-01-05'
                  AND player_id = 100001
                """,
                Integer.class
        );
        assertThat(zeroTours).isZero();
    }

    @Test
    void rejectsSameKeyEvenWhenRankChanges() {
        insertRanking("ATP", "2026-01-05", 100001, 1, 100, null);

        assertViolation(
                () -> insertRanking(
                        "ATP", "2026-01-05", 100001, 2, 90, null
                ),
                "23505",
                "pk_rankings"
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {100002, 999999})
    void rejectsPlayerMissingFromTheSameTour(int playerId) {
        assertViolation(
                () -> insertRanking(
                        "WTA", "2026-01-05", playerId, 1, null, null
                ),
                "23503",
                "fk_rankings_player"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "XYZ, 100001,  1, 100,  , ck_rankings_tour",
            "ATP,      0,  1, 100,  , ck_rankings_player_id_positive",
            "ATP, 100001,  0, 100,  , ck_rankings_rank_positive",
            "ATP, 100001,  1,  -1,  , ck_rankings_points_nonnegative",
            "WTA, 100001,  1, 100, -1, ck_rankings_source_tours_nonnegative",
            "ATP, 100001,  1, 100,  0, ck_rankings_source_tours_wta_only"
    })
    void rejectsInvalidValues(
            String tour,
            int playerId,
            int rank,
            Integer points,
            Integer sourceTours,
            String expectedConstraint
    ) {
        assertViolation(
                () -> insertRanking(
                        tour, "2026-01-05", playerId,
                        rank, points, sourceTours
                ),
                "23514",
                expectedConstraint
        );
    }

    private void insertRanking(
            String tour,
            String date,
            int playerId,
            int rank,
            Integer points,
            Integer sourceTours
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO rankings (
                    tour, ranking_date, player_id,
                    rank, points, source_tours
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                tour, LocalDate.parse(date), playerId,
                rank, points, sourceTours
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