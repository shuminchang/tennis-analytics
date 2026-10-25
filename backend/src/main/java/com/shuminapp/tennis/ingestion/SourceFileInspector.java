package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;

public final class SourceFileInspector {

    private final SourcePathResolver pathResolver = new SourcePathResolver();
    private final SourceFileHasher hasher = new SourceFileHasher();
    private final CsvFileInspector csvInspector = new CsvFileInspector();

    public InspectionResult inspect(
            Path snapshotRoot,
            Path relativePath,
            List<String> expectedHeader
    ) throws IOException {
        Path file = pathResolver.resolve(snapshotRoot, relativePath);

        BasicFileAttributes before = Files.readAttributes(
                file, BasicFileAttributes.class
        );

        String sha256 = hasher.sha256(file);
        long recordCount = csvInspector.countRecords(file, expectedHeader);

        Path checkedFile = pathResolver.resolve(snapshotRoot, relativePath);

        BasicFileAttributes after = Files.readAttributes(
                checkedFile, BasicFileAttributes.class
        );

        if (!file.equals(checkedFile)
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException(
                    "Source file changed during inspection: " + relativePath
            );
        }

        return new InspectionResult(
                relativePath.normalize(),
                sha256,
                before.size(),
                recordCount
        );
    }

    public record InspectionResult(
            Path relativePath,
            String sha256,
            long byteSize,
            long recordCount
    ) {
    }
}