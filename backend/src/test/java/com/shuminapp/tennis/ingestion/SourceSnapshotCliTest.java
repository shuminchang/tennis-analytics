package com.shuminapp.tennis.ingestion;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSnapshotCliTest {
    @TempDir Path temp;

    @Test void printsPlanForLocalCommit() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        fixture.write("atp/atp_players.csv", GitFixture.PLAYERS);
        var identity = fixture.commit();
        var output = new ByteArrayOutputStream();
        assertEquals(0, SourceSnapshotCli.run(new String[]{"plan", fixture.root.toString(),
                GitFixture.URL.toString(), identity.commitSha()}, new PrintStream(output), System.err));
        assertTrue(output.toString().contains("atp/atp_players.csv"));
        assertTrue(output.toString().contains(identity.commitSha()));
    }

    @Test void reportsUsageForUnknownCommand() {
        assertEquals(2, SourceSnapshotCli.run(new String[]{"unknown"}, System.out, System.err));
        assertEquals(2, SourceSnapshotCli.run(new String[]{}, System.out, System.err));
    }

    @Test void doesNotEchoSecretInInvalidUrl() {
        var output = new ByteArrayOutputStream();
        int status = SourceSnapshotCli.run(new String[]{"plan", temp.toString(),
                "https://user:secret@example.com/repo", "a".repeat(40)}, System.out, new PrintStream(output));
        assertEquals(1, status);
        assertFalse(output.toString().contains("secret"));
    }

    @Test void missingRepositoryFailsWithoutStartingApplication() {
        assertEquals(1, SourceSnapshotCli.run(new String[]{"plan", temp.resolve("missing").toString(),
                "https://example.com/repo", "a".repeat(40)}, System.out, System.err));
    }
}
