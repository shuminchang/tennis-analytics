package com.shuminapp.tennis;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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
class TournamentDecisionsSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    private Long snapshotId;
    private Long otherSnapshotId;
    private Long runId;
    private Long fileId;

    @BeforeEach
    void createFixtures() {
        snapshotId = insertSnapshot("a");
        otherSnapshotId = insertSnapshot("b");
        runId = insertRun(snapshotId);
        fileId = jdbcTemplate.queryForObject("""
                INSERT INTO source_files
                (snapshot_id, relative_path, tour, data_kind, sha256, byte_size, record_count)
                VALUES (?, 'atp/matches.csv', 'ATP', 'MATCHES', ?, 100, 2) RETURNING id
                """, Long.class, snapshotId, "c".repeat(64));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INCLUDE", "EXCLUDE", "REVIEW"})
    void savesDecisionWithoutFormalMatch(String status) {
        insertDecision(status);
        assertThat(jdbcTemplate.queryForObject("SELECT scope_status FROM tournament_decisions",
                String.class)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matches", Long.class)).isZero();
    }

    @Test
    void keepsIndependentAttempts() {
        insertDecision("REVIEW");
        runId = insertRun(snapshotId);
        insertDecision("INCLUDE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tournament_decisions", Long.class)).isEqualTo(2L);
    }

    @Test
    void rejectsConflictingDecisionsWithinRun() {
        insertDecision("REVIEW");
        assertViolation(() -> insertDecision("INCLUDE"), "23505", "pk_tournament_decisions");
    }

    @Test
    void rejectsRunFromAnotherSnapshot() {
        runId = insertRun(otherSnapshotId);
        assertViolation(() -> insertDecision("REVIEW"), "23503", "fk_tournament_decisions_run");
    }

    @Test
    void rejectsFileFromAnotherSnapshot() {
        jdbcTemplate.update("UPDATE source_files SET snapshot_id = ? WHERE id = ?", otherSnapshotId, fileId);
        assertViolation(() -> insertDecision("REVIEW"), "23503", "fk_tournament_decisions_file");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tour = 'WTA'", "data_kind = 'RANKINGS'"})
    void rejectsWrongFileAttributes(String assignment) {
        jdbcTemplate.update("UPDATE source_files SET " + assignment);
        assertViolation(() -> insertDecision("REVIEW"), "23503", "fk_tournament_decisions_file");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "tourney_id = ' '|ck_tournament_decisions_id",
        "record_number = 0|ck_tournament_decisions_record",
        "data_kind = 'PLAYERS'|ck_tournament_decisions_kind",
        "scope_status = 'MAYBE'|ck_tournament_decisions_status",
        "analysis_category = ' '|ck_tournament_decisions_category",
        "rule_code = ' '|ck_tournament_decisions_rule",
        "reason = ' '|ck_tournament_decisions_reason",
        "evidence = ' '|ck_tournament_decisions_evidence"
    })
    void rejectsInvalidDecisionDetails(String assignment, String constraint) {
        insertDecision("REVIEW");
        assertViolation(() -> jdbcTemplate.update("UPDATE tournament_decisions SET " + assignment),
                "23514", constraint);
    }

    private void insertDecision(String status) {
        jdbcTemplate.update("""
                INSERT INTO tournament_decisions (run_id, snapshot_id, tour, tourney_id,
                    source_file_id, record_number, scope_status, rule_code, reason, evidence)
                VALUES (?, ?, 'ATP', '2026-test', ?, 1, ?, 'DEFAULT_REVIEW',
                    'Classification not confirmed', 'scope-rules: default REVIEW')
                """, runId, snapshotId, fileId, status);
    }
    private Long insertSnapshot(String hexCharacter) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO source_snapshots (
                    repository_url, commit_sha, archive_location
                )
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "https://example.com/test/tennis-data.git",
                hexCharacter.repeat(40),
                "/test-archive/" + hexCharacter
        );
    }

    private Long insertRun(Long sourceSnapshotId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO import_runs (
                    snapshot_id, importer_commit_sha,
                    transform_rules_sha256,
                    quality_rules_sha256,
                    scope_rules_sha256
                )
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                sourceSnapshotId,
                "d".repeat(40),
                "e".repeat(64),
                "f".repeat(64),
                "0".repeat(64)
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
