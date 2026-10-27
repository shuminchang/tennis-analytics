package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies files before acquiring database locks; callers must keep the archive immutable. */
@Service
public class SourceRegistrationService {
    private final SourceSnapshotRepository snapshots;
    private final SourceFileRepository files;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SourceRegistrationService(SourceSnapshotRepository snapshots, SourceFileRepository files,
            JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.snapshots = snapshots;
        this.files = files;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public Registration register(Path repository, SourceSnapshotIdentity identity, Path archive) throws IOException {
        Path canonical = archive.toRealPath();
        var manifest = new SourceSnapshotStore().verify(repository, identity, archive);
        return registerVerified(manifest, canonical);
    }

    // Package-private: only the verified entry point above is exposed to application callers.
    Registration registerVerified(SourceSnapshotManifest manifest, Path archive) {
        if (manifest.files().isEmpty()) throw new IllegalArgumentException("Empty source inventory");
        return transaction.execute(status -> {
            long snapshotId = snapshots.register(manifest.identity(), archive);
            Map<String, Long> ids = new LinkedHashMap<>();
            for (var file : manifest.files()) {
                if (ids.putIfAbsent(file.relativePath(), files.register(snapshotId, file)) != null) {
                    throw new IllegalArgumentException("Duplicate source path");
                }
            }
            Long count = jdbc.queryForObject("SELECT count(*) FROM source_files WHERE snapshot_id = ?",
                    Long.class, snapshotId);
            if (count == null || count != ids.size()) {
                throw new IllegalStateException("Registered inventory differs from verified manifest");
            }
            return new Registration(snapshotId, manifest, ids);
        });
    }

    public record Registration(long snapshotId, SourceSnapshotManifest manifest, Map<String, Long> fileIds) {
        public Registration { fileIds = Map.copyOf(fileIds); }
    }
}
