package com.shuminapp.tennis.ingestion;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public SourceSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long register(
            SourceSnapshotIdentity identity,
            Path archiveLocation
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(archiveLocation, "archiveLocation");

        if (!archiveLocation.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Archive location must be an absolute path"
            );
        }

        String repositoryUrl = identity.repositoryUrl().toString();
        String commitSha = identity.commitSha();
        String location = archiveLocation.normalize().toString();

        jdbcTemplate.update("""
                INSERT INTO source_snapshots (
                    repository_url,
                    commit_sha,
                    archive_location
                )
                VALUES (?, ?, ?)
                ON CONFLICT (repository_url, commit_sha) DO NOTHING
                """,
                repositoryUrl,
                commitSha,
                location
        );

        SnapshotRow snapshot = jdbcTemplate.queryForObject("""
                SELECT id, archive_location
                FROM source_snapshots
                WHERE repository_url = ?
                  AND commit_sha = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new SnapshotRow(
                        resultSet.getLong("id"),
                        resultSet.getString("archive_location")
                ),
                repositoryUrl,
                commitSha
        );

        if (snapshot == null) {
            throw new IllegalStateException(
                    "Snapshot was not found after registration"
            );
        }

        if (!snapshot.archiveLocation().equals(location)) {
            throw new IllegalStateException(
                    "Snapshot is already registered at a different archive location"
            );
        }

        return snapshot.id();
    }

    private record SnapshotRow(long id, String archiveLocation) {
    }
}