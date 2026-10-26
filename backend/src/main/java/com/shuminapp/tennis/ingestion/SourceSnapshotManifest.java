package com.shuminapp.tennis.ingestion;

import java.util.List;

/** Describes selected raw files, not transformed or imported business data. */
public record SourceSnapshotManifest(
        int formatVersion,
        String selectionPolicy,
        SourceSnapshotIdentity identity,
        String treeId,
        List<SourceFile> files,
        List<SupportingFile> supportingFiles,
        List<String> excludedPaths
) {
    public static final String POLICY = "main-tour-singles-v1";

    public SourceSnapshotManifest {
        files = List.copyOf(files);
        supportingFiles = List.copyOf(supportingFiles);
        excludedPaths = List.copyOf(excludedPaths);
    }

    public record SourceFile(String relativePath, SourceFileType type, String blobId,
                             String sha256, long byteSize, long recordCount) { }

    public record SupportingFile(String relativePath, String blobId, String sha256, long byteSize) { }
}
