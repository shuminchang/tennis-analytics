package com.shuminapp.tennis.ingestion;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceFileRepository {

    private final JdbcTemplate jdbcTemplate;

    public SourceFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long register(
            long snapshotId,
            SourceSnapshotManifest.SourceFile file
    ) {
        Objects.requireNonNull(file, "file");

        if (snapshotId <= 0) {
            throw new IllegalArgumentException(
                    "Snapshot ID must be positive"
            );
        }

        Objects.requireNonNull(file.relativePath(), "relativePath");
        Objects.requireNonNull(file.type(), "type");

        Path path = Path.of(file.relativePath());
        SourceFileType expectedType = SourceFileType.fromPath(path);

        if (!path.toString().equals(file.relativePath())
                || !path.normalize().equals(path)
                || expectedType != file.type()) {
            throw new IllegalArgumentException(
                    "Source path and type must match the supported file format"
            );
        }

        if (file.sha256() == null
                || !file.sha256().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "SHA-256 must contain 64 lowercase hexadecimal characters"
            );
        }

        if (file.byteSize() < 0 || file.recordCount() < 0) {
            throw new IllegalArgumentException(
                    "Byte size and record count must not be negative"
            );
        }

        jdbcTemplate.update("""
                INSERT INTO source_files (
                    snapshot_id,
                    relative_path,
                    tour,
                    data_kind,
                    sha256,
                    byte_size,
                    record_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (snapshot_id, relative_path) DO NOTHING
                """,
                snapshotId,
                file.relativePath(),
                file.type().tour(),
                file.type().dataKind(),
                file.sha256(),
                file.byteSize(),
                file.recordCount()
        );

        FileRow stored = jdbcTemplate.queryForObject("""
                SELECT id, tour, data_kind, sha256, byte_size, record_count
                FROM source_files
                WHERE snapshot_id = ?
                  AND relative_path = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new FileRow(
                        resultSet.getLong("id"),
                        resultSet.getString("tour"),
                        resultSet.getString("data_kind"),
                        resultSet.getString("sha256"),
                        resultSet.getLong("byte_size"),
                        resultSet.getLong("record_count")
                ),
                snapshotId,
                file.relativePath()
        );

        if (stored == null) {
            throw new IllegalStateException(
                    "Source file was not found after registration"
            );
        }

        if (!stored.tour().equals(file.type().tour())
                || !stored.dataKind().equals(file.type().dataKind())
                || !stored.sha256().equals(file.sha256())
                || stored.byteSize() != file.byteSize()
                || stored.recordCount() != file.recordCount()) {
            throw new IllegalStateException(
                    "Source file conflicts with its existing registration"
            );
        }

        return stored.id();
    }

    private record FileRow(
            long id,
            String tour,
            String dataKind,
            String sha256,
            long byteSize,
            long recordCount
    ) {
    }
}