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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class SourceSnapshotRepositoryTest {

    @Autowired
    private SourceSnapshotRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private SourceSnapshotIdentity identity;
    private Path archive;

    @BeforeEach
    void setUp() {
        String token = UUID.randomUUID().toString().replace("-", "");

        identity = new SourceSnapshotIdentity(
                URI.create("https://example.com/source-" + token),
                "a".repeat(40)
        );

        archive = Path.of("/test-archives", token);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE FROM source_snapshots
                WHERE repository_url = ?
                """,
                identity.repositoryUrl().toString()
        );
    }

    @Test
    void registersNewSnapshot() {
        long id = repository.register(identity, archive);

        assertThat(id).isPositive();
        assertThat(snapshotCount()).isEqualTo(1L);

        String storedCommit = jdbcTemplate.queryForObject("""
                SELECT commit_sha
                FROM source_snapshots
                WHERE id = ?
                """,
                String.class,
                id
        );

        assertThat(storedCommit).isEqualTo(identity.commitSha());
        assertThat(storedLocation(id)).isEqualTo(archive.toString());
    }

    @Test
    void repeatedRegistrationReturnsSameId() {
        long firstId = repository.register(identity, archive);
        long secondId = repository.register(identity, archive);

        assertThat(secondId).isEqualTo(firstId);
        assertThat(snapshotCount()).isEqualTo(1L);
    }

    @Test
    void rejectsDifferentArchiveWithoutChangingExistingSnapshot() {
        long id = repository.register(identity, archive);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.register(
                        identity,
                        archive.resolve("different")
                )
        );

        assertThat(exception.getMessage())
                .contains("different archive location");

        assertThat(snapshotCount()).isEqualTo(1L);
        assertThat(storedLocation(id)).isEqualTo(archive.toString());
    }

    @Test
    void rejectsRelativeArchiveWithoutInsertingSnapshot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.register(
                        identity,
                        Path.of("relative", "archive")
                )
        );

        assertThat(snapshotCount()).isZero();
    }

    @Test
    void registrationRollsBackWhenOuterTransactionFails() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    repository.register(identity, archive);

                    assertThat(snapshotCount()).isEqualTo(1L);

                    throw new IllegalStateException(
                            "Simulated file registration failure"
                    );
                })
        );

        assertThat(exception.getMessage())
                .isEqualTo("Simulated file registration failure");

        assertThat(snapshotCount()).isZero();
    }

    private long snapshotCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM source_snapshots
                WHERE repository_url = ?
                """,
                Long.class,
                identity.repositoryUrl().toString()
        );

        return count;
    }

    private String storedLocation(long id) {
        return jdbcTemplate.queryForObject("""
                SELECT archive_location
                FROM source_snapshots
                WHERE id = ?
                """,
                String.class,
                id
        );
    }
}