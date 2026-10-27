package com.shuminapp.tennis.ingestion;

import com.shuminapp.tennis.TennisApplication;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Explicit operator entry point. Never runs during normal web startup. */
public final class PlayerImportCli {
    private PlayerImportCli() { }
    public static void main(String[] args) throws Exception {
        if (args.length!=5 || !"candidate".equals(System.getenv("IMPORT_TARGET"))) {
            System.err.println("Set IMPORT_TARGET=candidate and DB_* to an isolated database. Arguments: <repo> <https-origin> <source-sha> <archive> <importer-sha>");
            System.exit(2); return;
        }
        var identity=new SourceSnapshotIdentity(URI.create(args[1]),args[2]);
        try(var context=new SpringApplicationBuilder(TennisApplication.class).web(WebApplicationType.NONE).run()) {
            var result=context.getBean(PlayerImportService.class).importPlayers(Path.of(args[0]),identity,Path.of(args[3]),args[4]);
            System.out.printf("run=%d imported=%d quarantined=%d reused=%s%n",result.runId(),result.imported(),result.quarantined(),result.reused());
        }
    }
}
