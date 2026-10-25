package com.shuminapp.tennis;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class MatchSourcesSchemaTest {

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

        fileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO source_files (
                    snapshot_id, relative_path, tour, data_kind,
                    sha256, byte_size, record_count
                )
                VALUES (?, 'atp/matches.csv', 'ATP', 'MATCHES', ?, 100, 2)
                RETURNING id
                """,
                Long.class,
                snapshotId, "c".repeat(64)
        );

        jdbcTemplate.update(
                """
                INSERT INTO players (tour, player_id)
                VALUES ('ATP', 100001),
                       ('WTA', 100001),
                       ('WTA', 100002)
                """
        );
        jdbcTemplate.update("""
                INSERT INTO matches (id, tour, tourney_id, tourney_date, round,
                                     match_num, winner_id, loser_id)
                VALUES (100001, 'ATP', 'test', DATE '2026-01-05', 'F', 1, 100001, 100001),
                       (100002, 'WTA', 'test', DATE '2026-01-05', 'F', 1, 100001, 100002)
                """);
    }

    @Test
    void preservesMultipleSourceRowsAndSeparateAttempts() {
        insertSource(runId, 1, "ATP", 100001);
        insertSource(runId, 2, "ATP", 100001);

        Long retryRunId = insertRun(snapshotId);
        insertSource(retryRunId, 1, "ATP", 100001);

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM match_sources
                WHERE tour = 'ATP' AND match_id = 100001
                """,
                Long.class
        );
        assertThat(count).isEqualTo(3L);

        Long playersKindCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM match_sources
                WHERE data_kind = 'MATCHES'
                """,
                Long.class
        );
        assertThat(playersKindCount).isEqualTo(3L);
    }

    @Test
    void rejectsDuplicateSourcePositionWithinRun() {
        insertSource(runId, 1, "ATP", 100001);

        assertViolation(
                () -> insertSource(runId, 1, "ATP", 100001),
                "23505",
                "pk_match_sources"
        );
    }

    @Test
    void rejectsRunFromAnotherSnapshot() {
        Long otherRunId = insertRun(otherSnapshotId);

        assertViolation(
                () -> insertSource(otherRunId, 1, "ATP", 100001),
                "23503",
                "fk_match_sources_run"
        );
    }

    @Test
    void rejectsFileFromAnotherSnapshot() {
        jdbcTemplate.update(
                "UPDATE source_files SET snapshot_id = ? WHERE id = ?",
                otherSnapshotId, fileId
        );

        assertViolation(
                () -> insertSource(runId, 1, "ATP", 100001),
                "23503",
                "fk_match_sources_file"
        );
    }

    @Test
    void rejectsFileFromAnotherTour() {
        // WTA 比賽確實存在，因此此案例專門驗證檔案巡迴。
        assertViolation(
                () -> insertSource(runId, 1, "WTA", 100002),
                "23503",
                "fk_match_sources_file"
        );
    }

    @Test
    void rejectsRankingFileAsMatchSource() {
        jdbcTemplate.update(
                "UPDATE source_files SET data_kind = 'RANKINGS' WHERE id = ?",
                fileId
        );

        assertViolation(
                () -> insertSource(runId, 1, "ATP", 100001),
                "23503",
                "fk_match_sources_file"
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {100002, 999999})
    void rejectsMatchMissingFromTheSameTour(int playerId) {
        assertViolation(
                () -> insertSource(runId, 1, "ATP", playerId),
                "23503",
                "fk_match_sources_match"
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonpositiveRecordNumber(long recordNumber) {
        assertViolation(
                () -> insertSource(runId, recordNumber, "ATP", 100001),
                "23514",
                "ck_match_sources_record_positive"
        );
    }

    @Test
    void rejectsChangingSourceKind() {
        insertSource(runId, 1, "ATP", 100001);

        assertViolation(
                () -> jdbcTemplate.update(
                        """
                        UPDATE match_sources
                        SET data_kind = 'RANKINGS'
                        WHERE run_id = ?
                          AND source_file_id = ?
                          AND record_number = 1
                        """,
                        runId, fileId
                ),
                "23514",
                "ck_match_sources_data_kind"
        );
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

    private void insertSource(
            Long sourceRunId,
            long recordNumber,
            String tour,
            int playerId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO match_sources (
                    run_id, snapshot_id, source_file_id,
                    record_number, tour, match_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                sourceRunId, snapshotId, fileId,
                recordNumber, tour, playerId
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
