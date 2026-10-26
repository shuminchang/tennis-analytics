package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shuminapp.tennis.ingestion.SourceFileInspector.InspectionResult;

public final class SourceFileBatchInspector {

    private final SourceFileInspector fileInspector = new SourceFileInspector();

    public List<InspectionResult> inspect(
            Path snapshotRoot,
            SourceFilePlan plan
    ) throws IOException {
        Objects.requireNonNull(snapshotRoot, "snapshotRoot");
        Objects.requireNonNull(plan, "plan");

        List<InspectionResult> results = new ArrayList<>();

        for (Path relativePath : plan.relativePaths()) {
            try {
                InspectionResult result = fileInspector.inspect(
                        snapshotRoot, relativePath
                );
                results.add(result);
            } catch (IOException | IllegalArgumentException exception) {
                throw new IOException(
                        "Source file batch inspection failed: " + relativePath,
                        exception
                );
            }
        }

        return List.copyOf(results);
    }
}