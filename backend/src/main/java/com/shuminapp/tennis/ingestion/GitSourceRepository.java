package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Reads local Git objects only. It never checks out, fetches, or modifies the repository. */
public final class GitSourceRepository {
    private static final long MAX_METADATA_BYTES = 16 * 1024 * 1024;
    private final Path repository;
    private final Duration timeout;

    public GitSourceRepository(Path repository) throws IOException {
        this(repository, Duration.ofMinutes(2));
    }

    GitSourceRepository(Path repository, Duration timeout) throws IOException {
        this.repository = repository.toRealPath();
        if (!Files.isDirectory(this.repository) || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Expected repository directory and positive timeout");
        }
        this.timeout = timeout;
    }

    public Snapshot readSnapshot(SourceSnapshotIdentity identity) throws IOException {
        String config = text("config", "--local", "--list");
        if (config.lines().anyMatch(line -> line.startsWith("extensions.partialclone=")
                || line.matches("remote\\..*\\.promisor=(true|yes|on|1)"))) {
            throw new IOException("Partial clones are not supported; use a complete local object store");
        }
        String origin = text("config", "--local", "--get", "remote.origin.url").strip();
        String expected = identity.repositoryUrl().toString();
        if (!origin.equals(expected) && !origin.equals(expected + ".git")) {
            // Do not include origin: a misconfigured remote could contain credentials.
            throw new IOException("Local origin does not match the expected repository URL");
        }
        if (!text("cat-file", "-t", identity.commitSha()).strip().equals("commit")) {
            throw new IOException("Expected an exact commit object, not a tag or tree");
        }
        String tree = text("rev-parse", "--verify", identity.commitSha() + "^{tree}").strip();
        if (!tree.matches("[0-9a-f]{40}")) {
            throw new IOException("Unsupported Git object format");
        }
        String listing = text("ls-tree", "-r", "-z", "--full-tree", identity.commitSha());
        List<Entry> selected = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        List<SupportingFile> supporting = new ArrayList<>();
        for (String row : listing.split("\u0000")) {
            if (row.isEmpty()) continue;
            int tab = row.indexOf('\t');
            if (tab < 0) throw new IOException("Invalid Git tree entry");
            String[] metadata = row.substring(0, tab).split(" ");
            String name = row.substring(tab + 1);
            SourceFileType type;
            try {
                type = SourceFileType.fromPath(Path.of(name));
            } catch (IllegalArgumentException exception) {
                if (isUnknownSourceCandidate(name)) {
                    throw new IOException("Unrecognized source candidate requires review: " + name);
                }
                if (List.of("LICENSE", "README.md", "atp/UPSTREAM_README.md",
                        "wta/UPSTREAM_README.md", "atp/matches_data_dictionary.txt",
                        "wta/matches_data_dictionary.txt").contains(name)) {
                    if (metadata.length != 3 || !metadata[1].equals("blob")
                            || !metadata[0].matches("100(644|755)")) {
                        throw new IOException("Supporting documentation must be a regular blob");
                    }
                    supporting.add(new SupportingFile(name, metadata[2]));
                }
                excluded.add(name);
                continue;
            }
            if (metadata.length != 3 || !metadata[1].equals("blob")
                    || !(metadata[0].equals("100644") || metadata[0].equals("100755"))
                    || !metadata[2].matches("[0-9a-f]{40}")) {
                throw new IOException("Selected source must be a regular Git blob: " + name);
            }
            selected.add(new Entry(name, metadata[2], type));
        }
        if (selected.isEmpty()) throw new IOException("Commit contains no supported source files");
        selected.sort(Comparator.comparing(Entry::relativePath));
        excluded.sort(String::compareTo);
        return new Snapshot(identity, tree, selected, excluded, supporting);
    }

    // Known non-main-tour datasets are deliberately excluded. New candidate names fail closed.
    private boolean isUnknownSourceCandidate(String path) {
        if (!(path.startsWith("atp/") || path.startsWith("wta/")) || !path.endsWith(".csv")) {
            return false;
        }
        String name = path.substring(path.indexOf('/') + 1);
        if (name.matches("(atp|wta)_matches_(qual_chall|futures|doubles)_[0-9]{4}\\.csv")
                || path.matches("wta/wta_matches_qual_itf_[0-9]{4}\\.csv")
                || name.equals("atp_matches_amateur.csv")) return false;
        return name.contains("players") || name.contains("rankings") || name.contains("matches");
    }

    public void exportBlob(String objectId, Path output) throws IOException {
        if (!objectId.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Expected a complete blob object ID");
        }
        // Reserve the destination rather than silently overwriting existing files.
        Files.createFile(output);
        try {
            execute(output, "cat-file", "blob", objectId);
        } catch (IOException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
    }

    private String text(String... arguments) throws IOException {
        Path output = Files.createTempFile("tennis-git-", ".out");
        try {
            execute(output, arguments);
            if (Files.size(output) > MAX_METADATA_BYTES) throw new IOException("Git metadata exceeds limit");
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(output))).toString();
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private void execute(Path output, String... arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of(
                "git", "--no-replace-objects", "--literal-pathspecs", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_NO_LAZY_FETCH", "1");
        builder.redirectOutput(output.toFile());
        // No stderr echo: remote config / local paths can contain sensitive values.
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("Local Git command timed out: " + arguments[0]);
            }
            if (process.exitValue() != 0) {
                throw new IOException("Local Git command failed: " + arguments[0]
                        + " (exit " + process.exitValue() + ")");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Local Git command interrupted", exception);
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    public record Entry(String relativePath, String blobId, SourceFileType type) { }

    public record SupportingFile(String relativePath, String blobId) { }

    public record Snapshot(SourceSnapshotIdentity identity, String treeId,
                           List<Entry> entries, List<String> excludedPaths, List<SupportingFile> supportingFiles) {
        public Snapshot {
            entries = List.copyOf(entries);
            excludedPaths = List.copyOf(excludedPaths);
            supportingFiles = List.copyOf(supportingFiles);
        }
    }
}
