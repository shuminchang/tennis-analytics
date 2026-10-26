package com.shuminapp.tennis.ingestion;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class GitFixture {
    static final URI URL = URI.create("https://example.com/tennis");
    static final String PLAYERS = "player_id,name_first,name_last,hand,dob,ioc,height,wikidata_id\n"
            + "100001,First,Last,R,20000101,USA,180,Q1\n";
    final Path root;

    GitFixture(Path root) throws Exception {
        this.root = Files.createDirectories(root);
        git("init", "--quiet");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        git("remote", "add", "origin", URL.toString());
    }

    void write(String name, String content) throws IOException {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    SourceSnapshotIdentity commit() throws Exception {
        git("add", ".");
        git("commit", "--quiet", "-m", "fixture");
        return new SourceSnapshotIdentity(URL, git("rev-parse", "HEAD").strip());
    }

    String git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Path output = Files.createTempFile("git-test-", ".out");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            builder.environment().keySet().removeIf(k -> k.startsWith("GIT_"));
            builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
            builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
            Process process = builder.start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Fixture Git timed out");
            }
            String result = Files.readString(output);
            if (process.exitValue() != 0) throw new IOException(result);
            return result;
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
