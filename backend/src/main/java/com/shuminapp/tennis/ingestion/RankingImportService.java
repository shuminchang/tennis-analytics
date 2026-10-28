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
public class RankingImportService {
    private final SourceRegistrationService registration;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RankingConverter converter = new RankingConverter();
    private final ObjectMapper json = new ObjectMapper();
    private final String rulesHash;
    public RankingImportService(SourceRegistrationService registration,JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.registration=registration; this.jdbc=jdbc; this.tx=new TransactionTemplate(manager);
        try(var input=getClass().getResourceAsStream("/ingestion/ranking-rules-v1.txt")) {
            if(input==null) throw new IllegalStateException("Missing ranking rules");
            rulesHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        } catch(Exception e) { throw new IllegalStateException("Cannot load ranking rules",e); }
    }
    public Result importRankings(Path repository,SourceSnapshotIdentity identity,Path archive,String commit) throws IOException {
        if(commit==null || !commit.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Expected importer commit SHA");
        var registered=registration.register(repository,identity,archive);
        return importRegistered(registered,archive.toRealPath(),commit);
    }
    Result importRegistered(SourceRegistrationService.Registration registered,Path archive,String commit) {
        var files=registered.manifest().files().stream().filter(f->f.type().dataKind().equals("RANKINGS")).toList();
        if(files.stream().map(f->f.type().tour()).distinct().count()!=2) throw new IllegalArgumentException("Both tours' ranking files required");
        Result begun=tx.execute(status -> {
            lock();
            if(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE status='RUNNING'",Long.class)>0)
                throw new IllegalStateException("Inspect interrupted/running import before retry");
            if(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE data_kind='PLAYERS' AND status='SUCCEEDED' AND snapshot_id=?",Long.class,registered.snapshotId())!=1)
                throw new IllegalStateException("Import same-snapshot players first");
            var previous=jdbc.queryForList("SELECT * FROM import_runs WHERE data_kind='RANKINGS' AND status='SUCCEEDED'");
            if(!previous.isEmpty()) {
                var row=previous.getFirst();
                if(previous.size()!=1 || ((Number)row.get("snapshot_id")).longValue()!=registered.snapshotId()
                        || !rulesHash.equals(row.get("transform_rules_sha256")) || !commit.equals(row.get("importer_commit_sha")))
                    throw new IllegalStateException("Replacement ranking import requires a new candidate database");
                return result(((Number)row.get("id")).longValue(),true);
            }
            if(jdbc.queryForObject("SELECT count(*) FROM rankings",Long.class)>0) throw new IllegalStateException("Candidate rankings must be empty");
            Long run=jdbc.queryForObject("""
                    INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,data_kind)
                    VALUES(?,?,?,?,?,'RANKINGS') RETURNING id
                    """,Long.class,registered.snapshotId(),commit,rulesHash,rulesHash,rulesHash);
            return new Result(run,0,0,0,false);
        });
        if(begun.reused()) return begun;
        try {
            return tx.execute(status -> {
                lock();
                jdbc.execute("""
                        CREATE TEMP TABLE ranking_stage (
                            source_file_id bigint,record_number bigint,tour varchar(3),ranking_date date,
                            player_id integer,rank integer,points integer,source_tours integer,error text,raw jsonb
                        ) ON COMMIT DROP
                        """);
                for(var file:files) stage(registered,archive,file);
                publish(registered.snapshotId(),begun.runId());
                jdbc.update("UPDATE import_runs SET status='SUCCEEDED',finished_at=clock_timestamp() WHERE id=? AND status='RUNNING'",begun.runId());
                return result(begun.runId(),false);
            });
        } catch(RuntimeException failure) {
            tx.executeWithoutResult(status -> jdbc.update("UPDATE import_runs SET status='FAILED',finished_at=clock_timestamp(),error_summary=? WHERE id=? AND status='RUNNING'",
                    "Ranking import failed: "+failure.getClass().getSimpleName(),begun.runId()));
            throw failure;
        }
    }
    private void lock() { jdbc.execute("SELECT pg_advisory_xact_lock(714031)"); }
    private void stage(SourceRegistrationService.Registration registration,Path archive,SourceSnapshotManifest.SourceFile file) {
        try {
            Path path=new SourcePathResolver().resolve(archive,Path.of(file.relativePath()));
            if(!new SourceFileHasher().sha256(path).equals(file.sha256())) throw new IOException("Ranking file changed before import");
            var format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(false).get();
            List<Object[]> batch=new ArrayList<>(1000);
            long count=0;
            try(var reader=Files.newBufferedReader(path);var csv=format.parse(reader)) {
                if(!csv.getHeaderNames().equals(file.type().expectedHeader())) throw new IOException("Ranking header mismatch");
                for(var record:csv) {
                    count++;
                    if(record.size()!=file.type().expectedHeader().size()) throw new IOException("Ranking row width mismatch");
                    var raw=record.toMap();var c=converter.convert(file.type().tour(),raw);
                    batch.add(new Object[]{registration.fileIds().get(file.relativePath()),count,file.type().tour(),c.date(),c.playerId(),c.rank(),c.points(),c.tours(),c.error(),c.error()==null?null:json.writeValueAsString(raw)});
                    if(batch.size()==1000) { flush(batch);batch.clear(); }
                }
            }
            flush(batch);
            if(count!=file.recordCount() || !new SourceFileHasher().sha256(path).equals(file.sha256())) throw new IOException("Ranking file changed during import");
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    private void flush(List<Object[]> batch) {
        if(!batch.isEmpty()) jdbc.batchUpdate("INSERT INTO ranking_stage VALUES(?,?,?,?,?,?,?,?,?,?::jsonb)",batch);
    }
    private void publish(long snapshot,long run) {
        jdbc.execute("CREATE INDEX ON ranking_stage(tour,ranking_date,player_id)");
        jdbc.execute("ANALYZE ranking_stage");
        jdbc.execute("""
                CREATE TEMP TABLE ranking_blocked ON COMMIT DROP AS
                SELECT tour,ranking_date,player_id,
                    CASE WHEN bool_or(error IS NOT NULL) THEN 'INVALID_KEY_MEMBER' ELSE 'CONFLICTING_RANKING' END AS reason
                FROM ranking_stage WHERE ranking_date IS NOT NULL AND player_id IS NOT NULL
                GROUP BY tour,ranking_date,player_id
                HAVING bool_or(error IS NOT NULL) OR count(DISTINCT ROW(rank,points,source_tours))>1
                """);
        jdbc.execute("CREATE UNIQUE INDEX ON ranking_blocked(tour,ranking_date,player_id)");
        jdbc.update("""
                CREATE TEMP TABLE ranking_players ON COMMIT DROP AS
                SELECT DISTINCT ps.tour,ps.player_id FROM player_sources ps JOIN import_runs r ON r.id=ps.run_id
                WHERE r.data_kind='PLAYERS' AND r.status='SUCCEEDED' AND ps.snapshot_id=?
                """,snapshot);
        jdbc.execute("CREATE UNIQUE INDEX ON ranking_players(tour,player_id)");
        jdbc.execute("ANALYZE ranking_players");
        jdbc.execute("""
                UPDATE ranking_stage s SET error=coalesce(s.error,b.reason)
                FROM ranking_blocked b WHERE s.tour=b.tour AND s.ranking_date=b.ranking_date AND s.player_id=b.player_id
                """);
        jdbc.execute("""
                UPDATE ranking_stage s SET error='MISSING_PLAYER'
                WHERE error IS NULL AND NOT EXISTS(SELECT 1 FROM ranking_players p WHERE p.tour=s.tour AND p.player_id=s.player_id)
                """);
        jdbc.update("""
                INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,action,reason,evidence)
                SELECT ?,?,source_file_id,record_number,tour,'RANKINGS',error,'QUARANTINE',error,
                    coalesce(raw,jsonb_build_object('ranking_date',ranking_date,'player',player_id,'rank',rank,'points',points,'tours',source_tours))
                FROM ranking_stage WHERE error IS NOT NULL
                """,run,snapshot);
        jdbc.update("INSERT INTO quality_issue_impacts(issue_id,metric_code) SELECT id,'RANKING_HISTORY' FROM quality_issues WHERE run_id=?",run);
        jdbc.execute("""
                INSERT INTO rankings(tour,ranking_date,player_id,rank,points,source_tours)
                SELECT tour,ranking_date,player_id,min(rank),min(points),min(source_tours)
                FROM ranking_stage WHERE error IS NULL GROUP BY tour,ranking_date,player_id
                """);
        jdbc.update("""
                INSERT INTO ranking_sources(run_id,snapshot_id,source_file_id,record_number,tour,ranking_date,player_id)
                SELECT ?,?,source_file_id,record_number,tour,ranking_date,player_id FROM ranking_stage WHERE error IS NULL
                """,run,snapshot);
    }
    private Result result(long run,boolean reused) {
        return new Result(run,
                jdbc.queryForObject("SELECT count(*) FROM (SELECT DISTINCT tour,ranking_date,player_id FROM ranking_sources WHERE run_id=?) r",Long.class,run),
                jdbc.queryForObject("SELECT count(*) FROM ranking_sources WHERE run_id=?",Long.class,run),
                jdbc.queryForObject("SELECT count(*) FROM quality_issues WHERE run_id=? AND action='QUARANTINE'",Long.class,run),reused);
    }
    public record Result(long runId,long imported,long sourceRecords,long quarantined,boolean reused) { }
}
