package com.shuminapp.tennis;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class ImportRunsSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long snapshotId;

    @BeforeEach
    void createSnapshot() {
        snapshotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO source_snapshots (
                    repository_url, commit_sha, archive_location
                )
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "https://example.com/test/tennis-data.git",
                "a".repeat(40),
                "/test-archive/snapshot-a"
        );
    }

    @Test
    void defaultsToRunningWithoutCompletionDetails() {
        Long runId = insertRun(snapshotId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT status, finished_at, error_summary
                FROM import_runs WHERE id = ?
                """,
                runId
        );

        assertThat(row.get("status")).isEqualTo("RUNNING");
        assertThat(row.get("finished_at")).isNull();
        assertThat(row.get("error_summary")).isNull();

        OffsetDateTime startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM import_runs WHERE id = ?",
                (rs, rowNum) ->
                        rs.getObject("started_at", OffsetDateTime.class),
                runId
        );
        assertThat(startedAt).isNotNull();
    }

    @Test
    void allowsSeparateAttemptsWithSameSnapshotAndVersions() {
        Long firstRun = insertRun(snapshotId);
        Long secondRun = insertRun(snapshotId);

        assertThat(firstRun).isNotEqualTo(secondRun);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_runs WHERE snapshot_id = ?",
                Long.class,
                snapshotId
        );
        assertThat(count).isEqualTo(2L);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCEEDED,",
            "FAILED, CSV parsing failed"
    })
    void acceptsConsistentCompletionDetails(
            String status,
            String errorSummary
    ) {
        Long runId = insertRun(snapshotId);

        jdbcTemplate.update(
                """
                UPDATE import_runs
                SET status = ?,
                    finished_at = started_at + INTERVAL '1 second',
                    error_summary = ?
                WHERE id = ?
                """,
                status, errorSummary, runId
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT status, finished_at, error_summary
                FROM import_runs WHERE id = ?
                """,
                runId
        );

        assertThat(row.get("status")).isEqualTo(status);
        assertThat(row.get("finished_at")).isNotNull();
        assertThat(row.get("error_summary")).isEqualTo(errorSummary);
    }

    @Test
    void rejectsMissingSnapshot() {
        assertViolation(
                () -> insertRun(-1L),
                "23503",
                "fk_import_runs_snapshot"
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "importer_commit_sha = 'xyz'    | ck_import_runs_importer_commit",
            "transform_rules_sha256 = 'xyz' | ck_import_runs_transform_rules",
            "quality_rules_sha256 = 'xyz'   | ck_import_runs_quality_rules",
            "scope_rules_sha256 = 'xyz'     | ck_import_runs_scope_rules"
    })
    void rejectsInvalidVersionFormats(
            String assignment,
            String constraint
    ) {
        Long runId = insertRun(snapshotId);

        assertViolation(
                () -> updateRun(runId, assignment),
                "23514",
                constraint
        );
    }

    @Test
    void rejectsUnknownStatus() {
        Long runId = insertRun(snapshotId);

        assertViolation(
                () -> updateRun(runId, "status = 'UNKNOWN'"),
                "23514",
                "ck_import_runs_status",
                "ck_import_runs_state_details"
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "RUNNING   | false |",
            "RUNNING   | true  | unexpected error",
            "SUCCEEDED | true  |",
            "SUCCEEDED | false | unexpected error",
            "FAILED    | true  | CSV parsing failed",
            "FAILED    | false |",
            "FAILED    | false | '   '"
    })
    void rejectsInconsistentStateDetails(
            String status,
            boolean omitFinish,
            String errorSummary
    ) {
        Long runId = insertRun(snapshotId);

        assertViolation(
                () -> jdbcTemplate.update(
                        """
                        UPDATE import_runs
                        SET status = ?,
                            finished_at = CASE
                                WHEN ? THEN NULL
                                ELSE started_at + INTERVAL '1 second'
                            END,
                            error_summary = ?
                        WHERE id = ?
                        """,
                        status, omitFinish, errorSummary, runId
                ),
                "23514",
                "ck_import_runs_state_details"
        );
    }

    @Test
    void rejectsFinishBeforeStart() {
        Long runId = insertRun(snapshotId);

        assertViolation(
                () -> jdbcTemplate.update(
                        """
                        UPDATE import_runs
                        SET status = 'SUCCEEDED',
                            finished_at = started_at - INTERVAL '1 second'
                        WHERE id = ?
                        """,
                        runId
                ),
                "23514",
                "ck_import_runs_time_order"
        );
    }

    private Long insertRun(Long sourceSnapshotId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO import_runs (
                    snapshot_id,
                    importer_commit_sha,
                    transform_rules_sha256,
                    quality_rules_sha256,
                    scope_rules_sha256
                )
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                sourceSnapshotId,
                "b".repeat(40),
                "c".repeat(64),
                "d".repeat(64),
                "e".repeat(64)
        );
    }

    private void updateRun(Long runId, String assignment) {
        // assignment 只來自本測試的固定常數，不接受外部輸入。
        jdbcTemplate.update(
                "UPDATE import_runs SET " + assignment + " WHERE id = ?",
                runId
        );
    }

    private void assertViolation(
            Runnable operation,
            String expectedSqlState,
            String... expectedConstraints
    ) {
        DataIntegrityViolationException error = assertThrows(
                DataIntegrityViolationException.class,
                operation::run
        );

        Throwable cause = error.getMostSpecificCause();
        assertThat(cause).isInstanceOf(SQLException.class);

        SQLException sqlError = (SQLException) cause;
        assertThat(sqlError.getSQLState()).isEqualTo(expectedSqlState);
        assertThat(sqlError.getMessage()).containsAnyOf(expectedConstraints);
    }
}