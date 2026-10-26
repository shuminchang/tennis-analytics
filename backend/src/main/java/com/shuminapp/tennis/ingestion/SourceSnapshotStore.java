package com.shuminapp.tennis.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Creates new, read-only selected-file archives and verifies them against trusted local Git. */
public final class SourceSnapshotStore {
    public static final String MANIFEST_NAME = "source-manifest.json";
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final SourceFileInspector inspector = new SourceFileInspector();
    private final SourceFileHasher hasher = new SourceFileHasher();

    public SourceSnapshotManifest prepare(Path repositoryPath, SourceSnapshotIdentity identity,
                                          Path destination) throws IOException {
        GitSourceRepository repository = new GitSourceRepository(repositoryPath);
        var snapshot = repository.readSnapshot(identity);
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent().toRealPath();
        target = parent.resolve(target.getFileName());
        if (target.startsWith(repositoryPath.toRealPath())) {
            throw new IOException("Archive destination must be outside the source repository");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive destination already exists");
        Path staging = Files.createTempDirectory(parent, ".tennis-source-");
        try {
            List<SourceSnapshotManifest.SourceFile> files = new ArrayList<>();
            for (var entry : snapshot.entries()) {
                Path file = staging.resolve(entry.relativePath());
                Files.createDirectories(file.getParent());
                repository.exportBlob(entry.blobId(), file);
                var result = inspector.inspect(staging, Path.of(entry.relativePath()));
                require(blobId(file).equals(entry.blobId()), "Exported blob differs from commit: " + entry.relativePath());
                files.add(new SourceSnapshotManifest.SourceFile(entry.relativePath(), result.type(), entry.blobId(),
                        result.sha256(), result.byteSize(), result.recordCount()));
            }
            List<SourceSnapshotManifest.SupportingFile> supporting = new ArrayList<>();
            for (var entry : snapshot.supportingFiles()) {
                Path file = staging.resolve(entry.relativePath());
                Files.createDirectories(file.getParent());
                repository.exportBlob(entry.blobId(), file);
                require(blobId(file).equals(entry.blobId()), "Supporting blob differs from commit");
                supporting.add(new SourceSnapshotManifest.SupportingFile(entry.relativePath(), entry.blobId(),
                        hasher.sha256(file), Files.size(file)));
            }
            SourceSnapshotManifest manifest = new SourceSnapshotManifest(1, SourceSnapshotManifest.POLICY,
                    identity, snapshot.treeId(), files, supporting, snapshot.excludedPaths());
            mapper.writerWithDefaultPrettyPrinter().writeValue(staging.resolve(MANIFEST_NAME).toFile(), manifest);
            checkInventory(staging, manifest);
            makeReadOnly(staging);
            // No REPLACE_EXISTING: a previous archive must never be silently overwritten.
            Files.move(staging, target);
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r-xr-xr-x"));
            return manifest;
        } finally {
            if (Files.exists(staging)) deleteStaging(staging);
        }
    }

    public SourceSnapshotManifest verify(Path repositoryPath, SourceSnapshotIdentity expectedIdentity,
                                         Path archive) throws IOException {
        require(!Files.isSymbolicLink(archive), "Archive root must not be a symbolic link");
        Path root = archive.toRealPath();
        Path manifestPath = root.resolve(MANIFEST_NAME);
        require(Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS), "Missing regular manifest file");
        require(Files.size(manifestPath) <= 16 * 1024 * 1024, "Manifest exceeds size limit");
        SourceSnapshotManifest manifest;
        try {
            manifest = mapper.readValue(manifestPath.toFile(), SourceSnapshotManifest.class);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid manifest", exception);
        }
        require(manifest != null && manifest.formatVersion() == 1
                && SourceSnapshotManifest.POLICY.equals(manifest.selectionPolicy()), "Unsupported manifest format or selection policy");
        require(expectedIdentity.equals(manifest.identity()), "Manifest identity does not match requested snapshot");
        var snapshot = new GitSourceRepository(repositoryPath).readSnapshot(expectedIdentity);
        require(snapshot.treeId().equals(manifest.treeId()), "Commit tree mismatch");
        require(snapshot.excludedPaths().equals(manifest.excludedPaths()), "Excluded-path inventory mismatch");
        require(snapshot.entries().size() == manifest.files().size(), "Source file inventory size mismatch");
        require(snapshot.supportingFiles().size() == manifest.supportingFiles().size(), "Supporting inventory size mismatch");
        checkInventory(root, manifest);
        for (int i = 0; i < snapshot.entries().size(); i++) {
            var expected = snapshot.entries().get(i);
            var entry = manifest.files().get(i);
            require(expected.relativePath().equals(entry.relativePath()) && expected.type() == entry.type()
                    && expected.blobId().equals(entry.blobId()), "Source entry differs from commit selection");
            var actual = inspector.inspect(root, Path.of(entry.relativePath()));
            require(actual.sha256().equals(entry.sha256()) && actual.byteSize() == entry.byteSize()
                    && actual.recordCount() == entry.recordCount(), "Source content mismatch: " + entry.relativePath());
            require(blobId(root.resolve(entry.relativePath())).equals(entry.blobId()),
                    "Source blob mismatch: " + entry.relativePath());
        }
        for (int i = 0; i < snapshot.supportingFiles().size(); i++) {
            var expected = snapshot.supportingFiles().get(i);
            var entry = manifest.supportingFiles().get(i);
            require(expected.relativePath().equals(entry.relativePath()) && expected.blobId().equals(entry.blobId()),
                    "Supporting entry differs from commit");
            Path file = root.resolve(entry.relativePath());
            require(Files.size(file) == entry.byteSize() && hasher.sha256(file).equals(entry.sha256())
                    && blobId(file).equals(entry.blobId()), "Supporting document content mismatch");
        }
        return manifest;
    }

    private void checkInventory(Path root, SourceSnapshotManifest manifest) throws IOException {
        Set<String> expectedFiles = new HashSet<>(Set.of(MANIFEST_NAME));
        for (var entry : manifest.files()) addPath(expectedFiles, entry.relativePath());
        for (var entry : manifest.supportingFiles()) addPath(expectedFiles, entry.relativePath());
        Set<String> expectedDirectories = new HashSet<>(Set.of(""));
        for (String name : expectedFiles) {
            for (Path parent = Path.of(name).getParent(); parent != null; parent = parent.getParent()) {
                expectedDirectories.add(parent.toString());
            }
        }
        Set<String> actualFiles = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                require(!Files.isSymbolicLink(path), "Archive must not contain symbolic links");
                String relative = root.relativize(path).toString();
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    require(expectedDirectories.contains(relative), "Unexpected archive directory: " + relative);
                } else {
                    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Archive contains a non-regular file");
                    actualFiles.add(relative);
                }
            }
        }
        require(actualFiles.equals(expectedFiles), "Archive contains missing or extra files");
    }

    private void addPath(Set<String> paths, String name) throws IOException {
        require(name != null && !name.isBlank(), "Missing manifest path");
        Path path = Path.of(name);
        require(!path.isAbsolute() && path.normalize().equals(path) && !path.startsWith(".."), "Invalid manifest path");
        require(paths.add(name), "Duplicate manifest path");
    }

    private String blobId(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + Files.size(file) + "\0").getBytes(StandardCharsets.US_ASCII));
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 required for this repository object format", exception);
        }
    }

    private void makeReadOnly(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(root)) continue; // Keep staging root writable until rename completes.
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(
                        Files.isDirectory(path) ? "r-xr-xr-x" : "r--r--r--"));
            }
        }
    }

    private void deleteStaging(Path root) throws IOException {
        // Only our private staging directory is cleaned; never a caller's existing archive.
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
                }
            }
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
