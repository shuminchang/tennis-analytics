package com.shuminapp.tennis.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;

/** Standalone entry point: deliberately does not start Spring or connect to a database. */
public final class SourceSnapshotCli {
    private SourceSnapshotCli() {}

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 1 || !(args[0].equals("plan") || args[0].equals("prepare")
                || args[0].equals("verify"))
                || args.length != (args[0].equals("plan") ? 4 : 5)) {
            err.println("Usage: plan <local-repo> <https-origin> <commit-sha>");
            err.println("       prepare|verify <local-repo> <https-origin> <commit-sha> <archive>");
            return 2;
        }
        try {
            Path repository = Path.of(args[1]);
            var identity = new SourceSnapshotIdentity(URI.create(args[2]), args[3]);
            Object result = switch (args[0]) {
                case "plan" -> new GitSourceRepository(repository).readSnapshot(identity);
                case "prepare" -> new SourceSnapshotStore().prepare(repository, identity, Path.of(args[4]));
                case "verify" -> new SourceSnapshotStore().verify(repository, identity, Path.of(args[4]));
                default -> throw new IllegalStateException("Unknown command");
            };
            out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
            return 0;
        } catch (Exception exception) {
            // Avoid echoing untrusted arguments or nested Git errors containing credentials.
            err.println("Source operation failed (" + exception.getClass().getSimpleName()
                    + "). Check the local repository, commit, archive and file permissions.");
            return 1;
        }
    }
}
