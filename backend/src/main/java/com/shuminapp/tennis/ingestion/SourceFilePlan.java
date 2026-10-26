package com.shuminapp.tennis.ingestion;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SourceFilePlan(List<Path> relativePaths) {

    public SourceFilePlan {
        relativePaths = List.copyOf(relativePaths);

        if (relativePaths.isEmpty()) {
            throw new IllegalArgumentException(
                    "Source file plan must not be empty"
            );
        }

        Set<Path> seen = new HashSet<>();

        for (Path path : relativePaths) {
            SourceFileType.fromPath(path);

            if (!seen.add(path)) {
                throw new IllegalArgumentException(
                        "Duplicate source path: " + path
                );
            }
        }

        relativePaths = relativePaths.stream()
                .sorted(Comparator.comparing(Path::toString))
                .toList();
    }
}