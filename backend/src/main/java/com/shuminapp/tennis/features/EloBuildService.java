package com.shuminapp.tennis.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EloBuildService {
    private static final String INPUT="""
            SELECT id,tour,tourney_date,tourney_id,round,match_num,winner_id,loser_id,surface FROM v_stat_matches
            ORDER BY tour,tourney_date,tourney_id,CASE round WHEN 'RR' THEN 0 WHEN 'ER' THEN 1 WHEN 'R128' THEN 2 WHEN 'R64' THEN 3 WHEN 'R32' THEN 4 WHEN 'R16' THEN 5 WHEN 'QF' THEN 6 WHEN 'SF' THEN 7 WHEN 'BR' THEN 8 WHEN 'F' THEN 9 ELSE 10 END,match_num,id
            """;
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final String rulesHash;
    public EloBuildService(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try(var input=getClass().getResourceAsStream("/ingestion/elo-rules-v1.txt")) { if(input==null) throw new IllegalStateException();rulesHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())); }
        catch(Exception e) { throw new IllegalStateException("Missing Elo rules",e); }
    }
    public Result rebuild() {
        long run=tx.execute(s->{ jdbc.execute("SELECT pg_advisory_xact_lock(714031)");
            if(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE status='RUNNING'",Long.class)>0 || jdbc.queryForObject("SELECT count(*) FROM elo_runs WHERE status='RUNNING'",Long.class)>0) throw new IllegalStateException("Inspect running import/model before retry");
            return jdbc.queryForObject("INSERT INTO elo_runs(status,rules_sha256) VALUES('RUNNING',?) RETURNING id",Long.class,rulesHash); });
        try {
            return tx.execute(status->{
                jdbc.execute("SELECT pg_advisory_xact_lock(714031)");jdbc.execute("SET LOCAL statement_timeout=0");
                MessageDigest digest;try { digest=MessageDigest.getInstance("SHA-256"); } catch(Exception e) { throw new IllegalStateException(e); }
                jdbc.query(INPUT,ps->ps.setFetchSize(1000),(RowCallbackHandler)rs->{ for(int i=1;i<=9;i++) { String value=rs.getString(i);byte[] bytes=(value==null?"<null>":value).getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes); } });
                String fingerprint=HexFormat.of().formatHex(digest.digest());
                var prior=jdbc.queryForList("SELECT id,matches FROM elo_runs WHERE status='SUCCEEDED' AND rules_sha256=? AND input_sha256=? ORDER BY id DESC LIMIT 1",rulesHash,fingerprint);
                if(!prior.isEmpty()) { long reused=((Number)prior.getFirst().get("id")).longValue();jdbc.update("UPDATE elo_runs SET status='REUSED',input_sha256=?,reused_run_id=?,finished_at=clock_timestamp() WHERE id=?",fingerprint,reused,run);return new Result(reused,((Number)prior.getFirst().get("matches")).longValue(),true); }
                Map<Key,State> ratings=new HashMap<>();List<Object[]> batch=new ArrayList<>(1000);int[] sequence={0};
                jdbc.query(INPUT,ps->ps.setFetchSize(1000),(RowCallbackHandler)rs->{
                    sequence[0]++;String tour=rs.getString("tour"),surface=rs.getString("surface");int winner=rs.getInt("winner_id"),loser=rs.getInt("loser_id");LocalDate date=rs.getDate("tourney_date").toLocalDate();
                    if(winner==loser) throw new IllegalStateException("Invalid same-player model input");
                    for(String dimension:surface!=null && Set.of("Hard","Clay","Grass","Carpet").contains(surface)?List.of("OVERALL",surface):List.of("OVERALL")) {
                        var one=ratings.computeIfAbsent(new Key(tour,winner,dimension),key->new State());var two=ratings.computeIfAbsent(new Key(tour,loser,dimension),key->new State());
                        double beforeOne=one.rating,beforeTwo=two.rating;one.rating=EloMath.after(beforeOne,beforeTwo,one.matches,true);two.rating=EloMath.after(beforeTwo,beforeOne,two.matches,false);
                        one.matches++;two.matches++;one.date=date;two.date=date;
                        batch.add(new Object[]{run,tour,winner,rs.getLong("id"),dimension,sequence[0],date,beforeOne,one.rating,one.matches});
                        batch.add(new Object[]{run,tour,loser,rs.getLong("id"),dimension,sequence[0],date,beforeTwo,two.rating,two.matches});
                        if(batch.size()>=1000) { flush(batch);batch.clear(); }
                    }
                });flush(batch);batch.clear();
                for(var entry:ratings.entrySet()) {
                    var key=entry.getKey();var state=entry.getValue();batch.add(new Object[]{run,key.tour(),key.player(),key.dimension(),state.rating,state.matches,state.date});
                    if(batch.size()==1000) { jdbc.batchUpdate("INSERT INTO elo_current VALUES(?,?,?,?,?,?,?)",batch);batch.clear(); }
                }
                if(!batch.isEmpty()) jdbc.batchUpdate("INSERT INTO elo_current VALUES(?,?,?,?,?,?,?)",batch);
                jdbc.update("UPDATE elo_runs SET status='SUCCEEDED',input_sha256=?,matches=?,finished_at=clock_timestamp() WHERE id=?",fingerprint,sequence[0],run);
                return new Result(run,sequence[0],false);
            });
        } catch(RuntimeException failure) { tx.executeWithoutResult(s->jdbc.update("UPDATE elo_runs SET status='FAILED',finished_at=clock_timestamp(),error_summary=? WHERE id=?",failure.getClass().getSimpleName(),run));throw failure; }
    }
    private void flush(List<Object[]> rows) { if(!rows.isEmpty()) jdbc.batchUpdate("INSERT INTO elo_history VALUES(?,?,?,?,?,?,?,?,?,?)",rows); }
    private record Key(String tour,int player,String dimension) { }
    private static class State { double rating=1500;int matches;LocalDate date; }
    public record Result(long runId,long matches,boolean reused) { }
}
