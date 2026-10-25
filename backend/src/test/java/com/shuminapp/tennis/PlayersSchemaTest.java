package com.shuminapp.tennis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
@Transactional
class PlayersSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allowsSamePlayerIdAcrossToursWithMissingOptionalFields() {
        jdbcTemplate.update(
                "INSERT INTO players (tour, player_id) VALUES (?, ?)",
                "ATP", 100001
        );

        jdbcTemplate.update(
                "INSERT INTO players (tour, player_id) VALUES (?, ?)",
                "WTA", 100001
        );

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM players WHERE player_id = ?",
                Long.class,
                100001
        );

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void rejectsDuplicatePlayerWithinSameTour() {
        jdbcTemplate.update(
                "INSERT INTO players (tour, player_id) VALUES (?, ?)",
                "ATP", 100001
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO players (tour, player_id) VALUES (?, ?)",
                "ATP", 100001
        )).isInstanceOf(DuplicateKeyException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"R", "L", "U", "A"})
    void acceptsKnownHandsAndPreservesSuspiciousPositiveHeight(String hand) {
        jdbcTemplate.update(
                """
                INSERT INTO players (
                    tour, player_id, name_first, name_last,
                    hand, dob, ioc, height_cm, wikidata_id
                )
                VALUES (?, ?, ?, ?, ?, DATE '2000-01-01', ?, ?, ?)
                """,
                "ATP", 100001, "Test", "Player",
                hand, "TPE", 3, "Q123"
        );

        Integer height = jdbcTemplate.queryForObject(
                """
                SELECT height_cm
                FROM players
                WHERE tour = ? AND player_id = ?
                """,
                Integer.class,
                "ATP", 100001
        );

        assertThat(height).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "XYZ, 100001, R, USA, 180, Q123, ck_players_tour",
            "ATP,      0, R, USA, 180, Q123, ck_players_id_positive",
            "ATP, 100001, X, USA, 180, Q123, ck_players_hand",
            "ATP, 100001, R, usa, 180, Q123, ck_players_ioc",
            "ATP, 100001, R, USA,   0, Q123, ck_players_height_positive",
            "ATP, 100001, R, USA, 180,   Q0, ck_players_wikidata"
    })
    void rejectsInvalidValues(
            String tour,
            int playerId,
            String hand,
            String ioc,
            int height,
            String wikidataId,
            String expectedConstraint
    ) {
        DataIntegrityViolationException error = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO players (
                            tour, player_id, hand, ioc, height_cm, wikidata_id
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        tour, playerId, hand, ioc, height, wikidataId
                )
        );

        Throwable cause = error.getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);

        SQLException sqlError = (SQLException) cause;
        assertThat(sqlError.getSQLState()).isEqualTo("23514");
        assertThat(sqlError.getMessage()).contains(expectedConstraint);
    }
}