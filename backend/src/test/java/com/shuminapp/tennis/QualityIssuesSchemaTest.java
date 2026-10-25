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
class QualityIssuesSchemaTest {

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

    @Test
    void savesQuarantinedRawRowWithoutFormalEntityAndMultipleImpacts() {
        Long issue = insertIssue();
        jdbcTemplate.update("""
                INSERT INTO quality_issue_impacts (issue_id, metric_code)
                VALUES (?, 'WIN_LOSS'), (?, 'H2H'), (?, 'ELO')
                """, issue, issue, issue);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quality_issue_impacts WHERE issue_id = ?",
                Long.class, issue)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matches", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT evidence ->> 'winner_id' FROM quality_issues WHERE id = ?",
                String.class, issue)).isEqualTo("bad-id");
    }

    @Test
    void keepsDifferentRulesRolesFieldsAndAttempts() {
        insertIssue();
        jdbcTemplate.update("UPDATE quality_issues SET role = 'WINNER'");
        insertIssue();
        jdbcTemplate.update("UPDATE quality_issues SET field_name = 'winner_id'");
        insertIssue();
        runId = insertRun(snapshotId);
        insertIssue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quality_issues", Long.class)).isEqualTo(4L);
    }

    @Test
    void rejectsDuplicateIssue() {
        insertIssue();
        assertViolation(this::insertIssue, "23505", "uq_quality_issues_source_rule");
    }

    @Test
    void rejectsRunFromAnotherSnapshot() {
        runId = insertRun(otherSnapshotId);
        assertViolation(this::insertIssue, "23503", "fk_quality_issues_run");
    }

    @Test
    void rejectsFileFromAnotherSnapshot() {
        jdbcTemplate.update("UPDATE source_files SET snapshot_id = ? WHERE id = ?", otherSnapshotId, fileId);
        assertViolation(this::insertIssue, "23503", "fk_quality_issues_file");
    }

    @ParameterizedTest
    @ValueSource(strings = {"source_file_id = -1", "tour = 'WTA'", "data_kind = 'PLAYERS'"})
    void rejectsMismatchedFile(String assignment) {
        insertIssue();
        // SQL fragments are test constants, never external input.
        assertViolation(() -> jdbcTemplate.update("UPDATE quality_issues SET " + assignment),
                "23503", "fk_quality_issues_file");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "record_number = 0|ck_quality_issues_record_positive",
        "record_number = -1|ck_quality_issues_record_positive",
        "rule_code = ' '|ck_quality_issues_rule",
        "field_name = ' '|ck_quality_issues_field",
        "role = 'OTHER'|ck_quality_issues_role",
        "action = 'DELETE'|ck_quality_issues_action",
        "reason = ' '|ck_quality_issues_reason",
        "evidence = '[]'::jsonb|ck_quality_issues_evidence",
        "evidence = 'null'::jsonb|ck_quality_issues_evidence",
        "resolution_note = 'done'|ck_quality_issues_resolution",
        "status = 'RESOLVED'|ck_quality_issues_resolution",
        "status = 'DISMISSED', resolved_at = CURRENT_TIMESTAMP, resolution_note = ' '|ck_quality_issues_resolution",
        "status = 'RESOLVED', resolved_at = created_at - INTERVAL '1 second', resolution_note = 'fixed'|ck_quality_issues_resolution"
    })
    void rejectsInvalidIssueDetails(String assignment, String constraint) {
        insertIssue();
        assertViolation(() -> jdbcTemplate.update("UPDATE quality_issues SET " + assignment),
                "23514", constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESOLVED", "DISMISSED"})
    void acceptsDocumentedResolution(String status) {
        Long issue = insertIssue();
        jdbcTemplate.update("""
                UPDATE quality_issues SET status = ?, resolution_note = 'Reviewed with evidence',
                resolved_at = CURRENT_TIMESTAMP WHERE id = ?
                """, status, issue);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM quality_issues WHERE id = ?",
                String.class, issue)).isEqualTo(status);
    }

    @Test
    void rejectsDuplicateImpact() {
        Long issue = insertIssue();
        jdbcTemplate.update("INSERT INTO quality_issue_impacts VALUES (?, 'H2H')", issue);
        assertViolation(() -> jdbcTemplate.update("INSERT INTO quality_issue_impacts VALUES (?, 'H2H')", issue),
                "23505", "pk_quality_issue_impacts");
    }

    @Test
    void rejectsImpactWithoutIssue() {
        assertViolation(() -> jdbcTemplate.update("INSERT INTO quality_issue_impacts VALUES (-1, 'H2H')"),
                "23503", "quality_issue_impacts_issue_id_fkey");
    }

    @Test
    void rejectsBlankImpact() {
        Long issue = insertIssue();
        assertViolation(() -> jdbcTemplate.update("INSERT INTO quality_issue_impacts VALUES (?, ' ')", issue),
                "23514", "ck_quality_issue_impacts_metric");
    }

    private Long insertIssue() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO quality_issues (run_id, snapshot_id, source_file_id, record_number,
                    tour, data_kind, rule_code, action, reason, evidence)
                VALUES (?, ?, ?, 1, 'ATP', 'MATCHES', 'INVALID_ID', 'QUARANTINE',
                    'Cannot parse player ID', '{"winner_id":"bad-id"}'::jsonb) RETURNING id
                """, Long.class, runId, snapshotId, fileId);
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
