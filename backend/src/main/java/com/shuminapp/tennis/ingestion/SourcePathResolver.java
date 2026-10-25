package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class SourcePathResolver {

    public Path resolve(Path snapshotRoot, Path relativePath)
            throws IOException {
        Objects.requireNonNull(snapshotRoot, "snapshotRoot");
        Objects.requireNonNull(relativePath, "relativePath");

        if (relativePath.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Source path must not be blank"
            );
        }

        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Source path must be relative"
            );
        }

        for (Path component : relativePath) {
            if (component.toString().equals("..")) {
                throw new IllegalArgumentException(
                        "Source path must not contain '..'"
                );
            }
        }

        Path realRoot = snapshotRoot.toRealPath();

        if (!Files.isDirectory(realRoot)) {
            throw new IOException(
                    "Snapshot root must be a directory: " + snapshotRoot
            );
        }

        Path realFile = realRoot.resolve(relativePath).toRealPath();

        if (!realFile.startsWith(realRoot)) {
            throw new IllegalArgumentException(
                    "Source path resolves outside the snapshot root"
            );
        }

        if (!Files.isRegularFile(realFile)) {
            throw new IOException(
                    "Source must be a regular file: " + relativePath
            );
        }

        return realFile;
    }
}