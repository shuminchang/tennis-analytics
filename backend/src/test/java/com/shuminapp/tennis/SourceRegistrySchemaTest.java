package com.shuminapp.tennis;

import java.sql.SQLException;
import java.time.OffsetDateTime;

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
class SourceRegistrySchemaTest {

    private static final String REPOSITORY =
            "https://example.com/test/tennis-data.git";
    private static final String COMMIT_A = "a".repeat(40);
    private static final String COMMIT_B = "b".repeat(40);
    private static final String FILE_HASH = "c".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void acceptsRepeatedContentAcrossPathsAndSnapshotsWithZeroCounts() {
        Long firstSnapshot = insertSnapshot(COMMIT_A);
        Long secondSnapshot = insertSnapshot(COMMIT_B);

        insertFile(firstSnapshot, "atp/players.csv");
        insertFile(firstSnapshot, "atp/players-copy.csv");
        insertFile(secondSnapshot, "atp/players.csv");

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_files WHERE sha256 = ?",
                Long.class,
                FILE_HASH
        );
        assertThat(count).isEqualTo(3L);

        Long zeroCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM source_files
                WHERE byte_size = 0 AND record_count = 0
                """,
                Long.class
        );
        assertThat(zeroCount).isEqualTo(3L);

        OffsetDateTime registeredAt = jdbcTemplate.queryForObject(
                "SELECT registered_at FROM source_snapshots WHERE id = ?",
                (rs, rowNum) ->
                        rs.getObject("registered_at", OffsetDateTime.class),
                firstSnapshot
        );
        assertThat(registeredAt).isNotNull();
    }

    @Test
    void rejectsDuplicateRepositoryAndCommit() {
        insertSnapshot(COMMIT_A);

        assertViolation(
                () -> insertSnapshot(COMMIT_A),
                "23505",
                "uq_source_snapshots_repository_commit"
        );
    }

    @Test
    void rejectsDuplicatePathWithinSnapshot() {
        Long snapshotId = insertSnapshot(COMMIT_A);
        insertFile(snapshotId, "atp/players.csv");

        assertViolation(
                () -> insertFile(snapshotId, "atp/players.csv"),
                "23505",
                "uq_source_files_snapshot_path"
        );
    }

    @Test
    void rejectsFileWithoutExistingSnapshot() {
        assertViolation(
                () -> insertFile(-1L, "atp/players.csv"),
                "23503",
                "fk_source_files_snapshot"
        );
    }

    @Test
    void rejectsDeletingSnapshotReferencedByFiles() {
        Long snapshotId = insertSnapshot(COMMIT_A);
        insertFile(snapshotId, "atp/players.csv");

        assertViolation(
                () -> jdbcTemplate.update(
                        "DELETE FROM source_snapshots WHERE id = ?",
                        snapshotId
                ),
                "23503",
                "fk_source_files_snapshot"
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "repository_url = '   ' | ck_source_snapshots_repository_not_blank",
            "commit_sha = 'xyz'     | ck_source_snapshots_commit_sha",
            "archive_location = '' | ck_source_snapshots_archive_not_blank"
    })
    void rejectsInvalidSnapshotValues(
            String assignment,
            String constraint
    ) {
        Long snapshotId = insertSnapshot(COMMIT_A);

        // assignment 只來自固定測試資料，不接受外部輸入。
        assertViolation(
                () -> jdbcTemplate.update(
                        "UPDATE source_snapshots SET "
                                + assignment + " WHERE id = ?",
                        snapshotId
                ),
                "23514",
                constraint
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "relative_path = '' | ck_source_files_path_not_blank",
            "tour = 'XYZ'       | ck_source_files_tour",
            "data_kind = 'OTHER' | ck_source_files_data_kind",
            "sha256 = 'xyz'     | ck_source_files_sha256",
            "byte_size = -1     | ck_source_files_byte_size_nonnegative",
            "record_count = -1  | ck_source_files_record_count_nonnegative"
    })
    void rejectsInvalidFileValues(
            String assignment,
            String constraint
    ) {
        Long snapshotId = insertSnapshot(COMMIT_A);
        Long fileId = insertFile(snapshotId, "atp/players.csv");

        // assignment 只來自固定測試資料，不接受外部輸入。
        assertViolation(
                () -> jdbcTemplate.update(
                        "UPDATE source_files SET "
                                + assignment + " WHERE id = ?",
                        fileId
                ),
                "23514",
                constraint
        );
    }

    private Long insertSnapshot(String commitSha) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO source_snapshots (
                    repository_url, commit_sha, archive_location
                )
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                REPOSITORY,
                commitSha,
                "/test-archive/" + commitSha
        );
    }

    private Long insertFile(Long snapshotId, String relativePath) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO source_files (
                    snapshot_id, relative_path, tour, data_kind,
                    sha256, byte_size, record_count
                )
                VALUES (?, ?, 'ATP', 'PLAYERS', ?, 0, 0)
                RETURNING id
                """,
                Long.class,
                snapshotId,
                relativePath,
                FILE_HASH
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