package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class SourceFileRepositoryTest {

    @Autowired
    private SourceSnapshotRepository snapshotRepository;

    @Autowired
    private SourceFileRepository fileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long snapshotId;

    @BeforeEach
    void setUp() {
        String token = UUID.randomUUID().toString();

        var identity = new SourceSnapshotIdentity(
                URI.create("https://example.com/source-" + token),
                "a".repeat(40)
        );

        snapshotId = snapshotRepository.register(
                identity,
                Path.of("/test-archives", token)
        );
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM source_files WHERE snapshot_id = ?",
                snapshotId
        );

        jdbcTemplate.update(
                "DELETE FROM source_snapshots WHERE id = ?",
                snapshotId
        );
    }

    @Test
    void registersFileWithExpectedMetadata() {
        long id = fileRepository.register(snapshotId, validFile());

        assertThat(id).isPositive();
        assertThat(fileCount()).isEqualTo(1L);

        var stored = jdbcTemplate.queryForMap("""
                SELECT relative_path, tour, data_kind,
                       sha256, byte_size, record_count
                FROM source_files
                WHERE id = ?
                """,
                id
        );

        assertThat(stored)
                .containsEntry("relative_path", "atp/atp_players.csv")
                .containsEntry("tour", "ATP")
                .containsEntry("data_kind", "PLAYERS")
                .containsEntry("sha256", "b".repeat(64))
                .containsEntry("byte_size", 120L)
                .containsEntry("record_count", 1L);
    }

    @Test
    void repeatedRegistrationReturnsSameId() {
        long firstId = fileRepository.register(snapshotId, validFile());
        long secondId = fileRepository.register(snapshotId, validFile());

        assertThat(secondId).isEqualTo(firstId);
        assertThat(fileCount()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sha256", "byteSize", "recordCount"})
    void rejectsContentConflictWithoutOverwriting(String field) {
        long originalId = fileRepository.register(snapshotId, validFile());

        var conflicting = file(
                SourceFileType.ATP_PLAYERS,
                field.equals("sha256") ? "c".repeat(64) : "b".repeat(64),
                field.equals("byteSize") ? 121L : 120L,
                field.equals("recordCount") ? 2L : 1L
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fileRepository.register(snapshotId, conflicting)
        );

        assertThat(exception.getMessage())
                .contains("conflicts with its existing registration");

        // 原資料仍能以相同內容重新登錄，且 ID 不變。
        assertThat(fileRepository.register(snapshotId, validFile()))
                .isEqualTo(originalId);
        assertThat(fileCount()).isEqualTo(1L);
    }

    @Test
    void rejectsTypeThatDoesNotMatchPath() {
        var invalid = file(
                SourceFileType.WTA_PLAYERS,
                "b".repeat(64),
                120L,
                1L
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fileRepository.register(snapshotId, invalid)
        );

        assertThat(fileCount()).isZero();
    }

    @Test
    void rejectsMalformedHash() {
        var invalid = file(
                SourceFileType.ATP_PLAYERS,
                "not-a-sha256",
                120L,
                1L
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fileRepository.register(snapshotId, invalid)
        );

        assertThat(fileCount()).isZero();
    }

    @Test
    void rejectsMissingSnapshot() {
        jdbcTemplate.update(
                "DELETE FROM source_snapshots WHERE id = ?",
                snapshotId
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> fileRepository.register(snapshotId, validFile())
        );

        assertThat(fileCount()).isZero();
    }

    @Test
    void rollsBackFileWhenOuterTransactionFails() {
        var transaction = new TransactionTemplate(transactionManager);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    fileRepository.register(snapshotId, validFile());
                    assertThat(fileCount()).isEqualTo(1L);

                    throw new IllegalStateException("Simulated failure");
                })
        );

        assertThat(exception.getMessage()).isEqualTo("Simulated failure");
        assertThat(fileCount()).isZero();
    }

    private SourceSnapshotManifest.SourceFile validFile() {
        return file(SourceFileType.ATP_PLAYERS, "b".repeat(64), 120L, 1L);
    }

    private SourceSnapshotManifest.SourceFile file(
            SourceFileType type,
            String sha256,
            long byteSize,
            long recordCount
    ) {
        return new SourceSnapshotManifest.SourceFile(
                "atp/atp_players.csv",
                type,
                "d".repeat(40),
                sha256,
                byteSize,
                recordCount
        );
    }

    private long fileCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_files WHERE snapshot_id = ?",
                Long.class,
                snapshotId
        );
    }
}