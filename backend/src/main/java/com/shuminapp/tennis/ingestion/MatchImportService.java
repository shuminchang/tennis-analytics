package com.shuminapp.tennis.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MatchImportService {
    private final SourceRegistrationService registration;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MatchConverter converter=new MatchConverter();
    private final TournamentScope scope=new TournamentScope();
    private final ObjectMapper json=new ObjectMapper();
    private final String transformHash,scopeHash;
    public MatchImportService(SourceRegistrationService registration,JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.registration=registration;this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);
        transformHash=hash("match-rules-v1.txt");scopeHash=hash("tournament-policy-v1.txt","tournament-exceptions-v1.csv");
    }
    private String hash(String... names) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(String name:names) try(var input=getClass().getResourceAsStream("/ingestion/"+name)) {
                if(input==null) throw new IllegalStateException("Missing rules: "+name);
                digest.update(input.readAllBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(Exception e) { throw new IllegalStateException("Cannot load match rules",e); }
    }
    public Result importMatches(Path repository,SourceSnapshotIdentity identity,Path archive,String commit) throws IOException {
        if(commit==null || !commit.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Expected importer commit SHA");
        var registered=registration.register(repository,identity,archive);
        return importRegistered(registered,archive.toRealPath(),commit);
    }
    Result importRegistered(SourceRegistrationService.Registration registered,Path archive,String commit) {
        var files=registered.manifest().files().stream().filter(f->f.type().dataKind().equals("MATCHES")).toList();
        if(files.stream().map(f->f.type().tour()).distinct().count()!=2) throw new IllegalArgumentException("Both tours' annual singles files required");
        Result begun=tx.execute(status->{
            lock();
            if(jdbc.queryForObject("SELECT count(*) FROM elo_runs WHERE status='RUNNING'",Long.class)>0) throw new IllegalStateException("Inspect running Elo model before import");
            if(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE status='RUNNING'",Long.class)>0) throw new IllegalStateException("Inspect running/interrupted import first");
            if(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE data_kind='PLAYERS' AND status='SUCCEEDED' AND snapshot_id=?",Long.class,registered.snapshotId())!=1)
                throw new IllegalStateException("Import same-snapshot players first");
            var previous=jdbc.queryForList("SELECT * FROM import_runs WHERE data_kind='MATCHES' AND status='SUCCEEDED'");
            if(!previous.isEmpty()) {
                var row=previous.getFirst();
                if(previous.size()!=1 || ((Number)row.get("snapshot_id")).longValue()!=registered.snapshotId() || !commit.equals(row.get("importer_commit_sha"))
                        || !transformHash.equals(row.get("transform_rules_sha256")) || !scopeHash.equals(row.get("scope_rules_sha256")))
                    throw new IllegalStateException("Replacement match import requires a new candidate database");
                return result(((Number)row.get("id")).longValue(),true);
            }
            if(jdbc.queryForObject("SELECT count(*) FROM matches",Long.class)>0) throw new IllegalStateException("Candidate matches must be empty");
            Long run=jdbc.queryForObject("""
                    INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,data_kind)
                    VALUES(?,?,?,?,?,'MATCHES') RETURNING id
                    """,Long.class,registered.snapshotId(),commit,transformHash,transformHash,scopeHash);
            return new Result(run,0,0,0,false);
        });
        if(begun.reused()) return begun;
        try {
            return tx.execute(status->{
                lock();
                jdbc.execute("""
                        CREATE TEMP TABLE match_stage(source_file_id bigint,record_number bigint,tour varchar(3),
                            payload jsonb,raw jsonb,issues jsonb,error text,scope_status text,category text,scope_rule text,evidence text) ON COMMIT DROP
                        """);
                for(var file:files) stage(registered,archive,file);
                publish(registered.snapshotId(),begun.runId());
                jdbc.update("UPDATE import_runs SET status='SUCCEEDED',finished_at=clock_timestamp() WHERE id=? AND status='RUNNING'",begun.runId());
                return result(begun.runId(),false);
            });
        } catch(RuntimeException failure) {
            tx.executeWithoutResult(status->jdbc.update("UPDATE import_runs SET status='FAILED',finished_at=clock_timestamp(),error_summary=? WHERE id=? AND status='RUNNING'",
                    "Match import failed: "+failure.getClass().getSimpleName(),begun.runId()));
            throw failure;
        }
    }
    private void lock() { jdbc.execute("SELECT pg_advisory_xact_lock(714031)"); }
    private void stage(SourceRegistrationService.Registration registration,Path archive,SourceSnapshotManifest.SourceFile file) {
        try {
            Path path=new SourcePathResolver().resolve(archive,Path.of(file.relativePath()));
            if(!new SourceFileHasher().sha256(path).equals(file.sha256())) throw new IOException("Match file changed before import");
            var format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(false).get();
            List<Object[]> batch=new ArrayList<>(500);long count=0;
            try(var reader=Files.newBufferedReader(path);var csv=format.parse(reader)) {
                if(!csv.getHeaderNames().equals(file.type().expectedHeader())) throw new IOException("Match header mismatch");
                for(var row:csv) {
                    count++;if(row.size()!=file.type().expectedHeader().size()) throw new IOException("Match row width mismatch");
                    var raw=row.toMap();var c=converter.convert(file.type().tour(),raw);
                    var decision=scope.classify(file.type().tour(),raw.get("tourney_id"),raw.get("tourney_level"));
                    batch.add(new Object[]{registration.fileIds().get(file.relativePath()),count,file.type().tour(),json.writeValueAsString(c.fields()),json.writeValueAsString(raw),json.writeValueAsString(c.issues()),c.error(),decision.status(),decision.category(),decision.rule(),decision.evidence()});
                    if(batch.size()==500) { flush(batch);batch.clear(); }
                }
            }
            flush(batch);
            if(count!=file.recordCount() || !new SourceFileHasher().sha256(path).equals(file.sha256())) throw new IOException("Match file changed during import");
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    private void flush(List<Object[]> batch) { if(!batch.isEmpty()) jdbc.batchUpdate("INSERT INTO match_stage VALUES(?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?,?,?)",batch); }
    private void publish(long snapshot,long run) {
        jdbc.execute("""
                CREATE TEMP TABLE match_players ON COMMIT DROP AS
                SELECT DISTINCT p.tour,p.player_id,p.dob,
                    lower(regexp_replace(trim(concat_ws(' ',p.name_first,p.name_last)),'\\s+',' ','g')) ~ '^(u )?unknown( unknown)?$|^x( x)?$' AS placeholder
                FROM players p JOIN player_sources ps ON ps.tour=p.tour AND ps.player_id=p.player_id
                JOIN import_runs r ON r.id=ps.run_id
                WHERE r.status='SUCCEEDED' AND r.data_kind='PLAYERS' AND ps.snapshot_id=
                """+snapshot);
        jdbc.execute("CREATE UNIQUE INDEX ON match_players(tour,player_id)");
        jdbc.execute("""
                CREATE TEMP TABLE match_conflicts ON COMMIT DROP AS
                SELECT tour,payload->>'tourney_id' AS tourney_id,payload->>'round' AS round,payload->>'match_num' AS match_num
                FROM match_stage WHERE payload->>'tourney_id' IS NOT NULL AND payload->>'round' IS NOT NULL AND payload->>'match_num' IS NOT NULL
                GROUP BY tour,payload->>'tourney_id',payload->>'round',payload->>'match_num' HAVING count(DISTINCT raw)>1 OR bool_or(error IS NOT NULL)
                """);
        jdbc.execute("""
                UPDATE match_stage s SET error='CONFLICTING_MATCH' FROM match_conflicts c WHERE s.tour=c.tour
                    AND s.payload->>'tourney_id'=c.tourney_id AND s.payload->>'round'=c.round AND s.payload->>'match_num'=c.match_num
                """);
        if(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT tour,payload->>'tourney_id' FROM match_stage WHERE payload->>'tourney_id' IS NOT NULL
                GROUP BY tour,payload->>'tourney_id' HAVING count(DISTINCT ROW(payload->>'tourney_date',payload->>'tourney_name',payload->>'tourney_level'))>1) c
                """,Long.class)>0) throw new IllegalStateException("Tournament metadata changed within same identity; review before importing");
        jdbc.update("""
                INSERT INTO tournament_decisions(run_id,snapshot_id,tour,tourney_id,source_file_id,record_number,scope_status,analysis_category,rule_code,reason,evidence)
                SELECT DISTINCT ON(tour,payload->>'tourney_id') ?,?,tour,payload->>'tourney_id',source_file_id,record_number,scope_status,category,scope_rule,scope_rule,evidence
                FROM match_stage WHERE coalesce(btrim(payload->>'tourney_id'),'')<>'' ORDER BY tour,payload->>'tourney_id',source_file_id,record_number
                """,run,snapshot);
        jdbc.execute("""
                UPDATE match_stage s SET error='MISSING_PLAYER' WHERE error IS NULL AND (
                    NOT EXISTS(SELECT 1 FROM match_players p WHERE p.tour=s.tour AND p.player_id=(s.payload->>'winner_id')::integer)
                    OR NOT EXISTS(SELECT 1 FROM match_players p WHERE p.tour=s.tour AND p.player_id=(s.payload->>'loser_id')::integer))
                """);
        jdbc.update("""
                INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,action,reason,evidence)
                SELECT ?,?,source_file_id,record_number,tour,'MATCHES',error,'QUARANTINE',error,raw FROM match_stage WHERE error IS NOT NULL
                """,run,snapshot);
        jdbc.update("""
                INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,field_name,role,action,reason,evidence)
                SELECT ?,?,s.source_file_id,s.record_number,s.tour,'MATCHES',i->>'code',i->>'field',i->>'role','FLAG',i->>'code',s.raw
                FROM match_stage s CROSS JOIN LATERAL jsonb_array_elements(s.issues) i WHERE s.error IS NULL
                """,run,snapshot);
        for(String role:List.of("winner","loser")) {
            jdbc.update("""
                    INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,field_name,role,action,reason,evidence)
                    SELECT ?,?,s.source_file_id,s.record_number,s.tour,'MATCHES',
                        CASE WHEN p.placeholder THEN 'PLACEHOLDER_MASTER' ELSE 'BIRTH_AFTER_EVENT' END,?,?,'FLAG',
                        'Player identity requires review',s.raw
                    FROM match_stage s JOIN match_players p ON p.tour=s.tour AND p.player_id=(s.payload->>?)::integer
                    WHERE s.error IS NULL AND (p.placeholder OR p.dob>(s.payload->>'tourney_date')::date)
                    """,run,snapshot,role+"_id",role.toUpperCase(Locale.ROOT),role+"_id");
        }
        jdbc.update("""
                INSERT INTO quality_issue_impacts(issue_id,metric_code)
                SELECT q.id,CASE WHEN q.action='QUARANTINE' OR q.rule_code IN ('PLACEHOLDER_MASTER','BIRTH_AFTER_EVENT') THEN 'MATCH_HISTORY' ELSE i->>'metric' END
                FROM quality_issues q LEFT JOIN match_stage s ON s.source_file_id=q.source_file_id AND s.record_number=q.record_number
                LEFT JOIN LATERAL jsonb_array_elements(s.issues) i ON i->>'code'=q.rule_code AND i->>'field'=q.field_name AND i->>'role'=q.role
                WHERE q.run_id=?
                """,run);
        jdbc.update("""
                INSERT INTO quality_issue_impacts(issue_id,metric_code)
                SELECT q.id,'PERSONAL_STATS' FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id
                WHERE q.run_id=? AND i.metric_code='MATCH_HISTORY'
                """,run);
        List<String> columns=converter.convert("ATP",Map.of()).fields().keySet().stream().toList();
        String names=String.join(",",columns),selected=String.join(",",columns.stream().map(c->"m."+c).toList());
        jdbc.execute("INSERT INTO matches("+names+") SELECT "+selected+" FROM (SELECT DISTINCT ON(tour,payload->>'tourney_id',payload->>'round',payload->>'match_num') payload FROM match_stage WHERE error IS NULL) s CROSS JOIN LATERAL jsonb_populate_record(NULL::matches,s.payload) m");
        jdbc.update("""
                INSERT INTO match_sources(run_id,snapshot_id,source_file_id,record_number,tour,match_id)
                SELECT ?,?,s.source_file_id,s.record_number,s.tour,m.id FROM match_stage s JOIN matches m ON m.tour=s.tour
                    AND m.tourney_id=s.payload->>'tourney_id' AND m.round=s.payload->>'round' AND m.match_num=(s.payload->>'match_num')::integer
                WHERE s.error IS NULL
                """,run,snapshot);
    }
    private Result result(long run,boolean reused) {
        return new Result(run,jdbc.queryForObject("SELECT count(DISTINCT match_id) FROM match_sources WHERE run_id=?",Long.class,run),
                jdbc.queryForObject("SELECT count(*) FROM match_sources WHERE run_id=?",Long.class,run),
                jdbc.queryForObject("SELECT count(*) FROM quality_issues WHERE run_id=? AND action='QUARANTINE'",Long.class,run),reused);
    }
    public record Result(long runId,long imported,long sourceRecords,long quarantined,boolean reused) { }
}
