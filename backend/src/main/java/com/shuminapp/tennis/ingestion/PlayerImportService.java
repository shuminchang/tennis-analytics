package com.shuminapp.tennis.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlayerImportService {
    private final SourceRegistrationService registration;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PlayerConverter converter = new PlayerConverter();
    private final ObjectMapper json = new ObjectMapper();
    private final String rulesHash;

    public PlayerImportService(SourceRegistrationService registration, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.registration=registration; this.jdbc=jdbc; this.tx=new TransactionTemplate(manager);
        try (var input=getClass().getResourceAsStream("/ingestion/player-rules-v1.txt")) {
            if (input == null) throw new IllegalStateException("Missing player rules");
            rulesHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        } catch (Exception e) { throw new IllegalStateException("Cannot load player rules",e); }
    }

    public Result importPlayers(Path repository, SourceSnapshotIdentity identity, Path archive, String importerCommit) throws IOException {
        if (importerCommit == null || !importerCommit.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Expected importer commit SHA");
        var registered=registration.register(repository,identity,archive);
        return importRegistered(registered,archive.toRealPath(),importerCommit);
    }

    // Verified input only; package scope supports fixture tests without bypassing the public entry point in production.
    Result importRegistered(SourceRegistrationService.Registration registered, Path archive, String importerCommit) {
        var selected=registered.manifest().files().stream().filter(f -> f.type().dataKind().equals("PLAYERS")).toList();
        if (selected.size()!=2 || selected.stream().map(f->f.type().tour()).distinct().count()!=2) {
            throw new IllegalArgumentException("Full ATP and WTA player files are required");
        }
        Result begun=tx.execute(status -> {
            lock();
            if(jdbc.queryForObject("SELECT count(*) FROM elo_runs WHERE status='RUNNING'",Long.class)>0) throw new IllegalStateException("Inspect running Elo model before import");
            var successful=jdbc.queryForList("SELECT id,snapshot_id,transform_rules_sha256,importer_commit_sha FROM import_runs WHERE status='SUCCEEDED' AND data_kind='PLAYERS'");
            if (!successful.isEmpty()) {
                if (successful.size()!=1) throw new IllegalStateException("Use an isolated player candidate database");
                var row=successful.getFirst();
                if (((Number)row.get("snapshot_id")).longValue()!=registered.snapshotId()
                        || !rulesHash.equals(row.get("transform_rules_sha256")) || !importerCommit.equals(row.get("importer_commit_sha"))) {
                    throw new IllegalStateException("Replacement import requires a new candidate database");
                }
                return result(((Number)row.get("id")).longValue(),true);
            }
            if (jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE status='RUNNING'",Long.class)>0
                    || jdbc.queryForObject("SELECT count(*) FROM players",Long.class)>0) {
                throw new IllegalStateException("Candidate must have no players or running import; inspect interrupted runs before retry");
            }
            Long run=jdbc.queryForObject("""
                    INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256)
                    VALUES(?,?,?,?,?) RETURNING id
                    """,Long.class,registered.snapshotId(),importerCommit,rulesHash,rulesHash,rulesHash);
            return new Result(run,0,0,false);
        });
        if (begun.reused()) return begun;
        try {
            return tx.execute(status -> {
                lock();
                for (var file:selected) readFile(registered,archive,file,begun.runId());
                jdbc.update("UPDATE import_runs SET status='SUCCEEDED',finished_at=clock_timestamp() WHERE id=? AND status='RUNNING'",begun.runId());
                return result(begun.runId(),false);
            });
        } catch (RuntimeException failure) {
            tx.executeWithoutResult(status -> jdbc.update("UPDATE import_runs SET status='FAILED',finished_at=clock_timestamp(),error_summary=? WHERE id=? AND status='RUNNING'",
                    "Player import failed: "+failure.getClass().getSimpleName(),begun.runId()));
            throw failure;
        }
    }
    private void lock() { jdbc.execute("SELECT pg_advisory_xact_lock(714031)"); }
    private Result result(long run,boolean reused) {
        return new Result(run,jdbc.queryForObject("SELECT count(*) FROM player_sources WHERE run_id=?",Long.class,run),
                jdbc.queryForObject("SELECT count(*) FROM quality_issues WHERE run_id=? AND action='QUARANTINE'",Long.class,run),reused);
    }
    private void readFile(SourceRegistrationService.Registration registration,Path archive,SourceSnapshotManifest.SourceFile file,long run) {
        try {
            Path path=new SourcePathResolver().resolve(archive,Path.of(file.relativePath()));
            if (!new SourceFileHasher().sha256(path).equals(file.sha256())) throw new IOException("Player file changed before import");
            var format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(false).get();
            long count=0;
            Set<Integer> seen=new HashSet<>();
            try(var reader=Files.newBufferedReader(path);var csv=format.parse(reader)) {
                if (!csv.getHeaderNames().equals(file.type().expectedHeader())) throw new IOException("Player header mismatch");
                for(var record:csv) {
                    count++;
                    if (record.size()!=file.type().expectedHeader().size()) throw new IOException("Player row width mismatch");
                    Map<String,String> raw=record.toMap();
                    var converted=converter.convert(raw);
                    if(converted.player()!=null) {
                        var p=converted.player();
                        if(!seen.add(p.id())) throw new IllegalStateException("Duplicate player identity within file");
                        jdbc.update("INSERT INTO players(tour,player_id,name_first,name_last,hand,dob,ioc,height_cm,wikidata_id) VALUES(?,?,?,?,?,?,?,?,?)",
                                file.type().tour(),p.id(),p.first(),p.last(),p.hand(),p.dob(),p.ioc(),p.height(),p.wikidata());
                        jdbc.update("INSERT INTO player_sources(run_id,snapshot_id,source_file_id,record_number,tour,player_id) VALUES(?,?,?,?,?,?)",
                                run,registration.snapshotId(),registration.fileIds().get(file.relativePath()),count,file.type().tour(),p.id());
                    }
                    for(var issue:converted.issues()) {
                        Long issueId=jdbc.queryForObject("""
                                INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,field_name,role,action,reason,evidence)
                                VALUES(?,?,?,?,?,'PLAYERS',?,?,'PLAYER',?,?,?::jsonb) RETURNING id
                                """,Long.class,run,registration.snapshotId(),registration.fileIds().get(file.relativePath()),count,file.type().tour(),
                                issue.code(),issue.field(),issue.action(),issue.code(),json.writeValueAsString(raw));
                        jdbc.update("INSERT INTO quality_issue_impacts(issue_id,metric_code) VALUES(?,?)",issueId,issue.metric());
                    }
                }
            }
            if(count!=file.recordCount() || !new SourceFileHasher().sha256(path).equals(file.sha256())) throw new IOException("Player file changed during import");
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public record Result(long runId,long imported,long quarantined,boolean reused) { }
}
