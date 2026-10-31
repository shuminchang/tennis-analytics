package com.shuminapp.tennis.prediction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuminapp.tennis.TennisApplication;
import com.shuminapp.tennis.visualization.ResearchRepository;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Exports model inputs only, never imports or updates business data. */
public final class MatchupExportCli {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Provide a new CSV path and metadata path");
        Path csv=Path.of(args[0]),metadata=Path.of(args[1]);
        if(Files.exists(csv) || Files.exists(metadata)) throw new IllegalArgumentException("Output already exists");
        try(var context=new SpringApplicationBuilder(TennisApplication.class).web(WebApplicationType.NONE).run("--spring.flyway.enabled=false")) {
            var jdbc=context.getBean(JdbcTemplate.class);var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.setReadOnly(true);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            var result=tx.execute(status->{
                if(!"on".equals(jdbc.queryForObject("SHOW transaction_read_only",String.class))) throw new IllegalStateException("Read-only transaction required");
                var source=context.getBean(ResearchRepository.class).source();jdbc.execute("SET LOCAL statement_timeout='120s'");
                var model=jdbc.queryForMap("SELECT id,rules_sha256,input_sha256,matches FROM elo_runs WHERE status='SUCCEEDED' ORDER BY id DESC LIMIT 1");
                if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM elo_runs WHERE status='RUNNING')",Boolean.class)) throw new IllegalStateException("Elo build running");
                var output=new LinkedHashMap<String,Object>();output.put("source",source);output.put("elo",model);
                String query="""
                    WITH blocks AS MATERIALIZED (
                      SELECT s.match_id,q.role,bool_or(i.metric_code='AGE_ANALYSIS') AS age_blocked,bool_or(i.metric_code='HEIGHT_ANALYSIS') AS height_blocked
                      FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id
                      JOIN match_sources s ON s.run_id=q.run_id AND s.source_file_id=q.source_file_id AND s.record_number=q.record_number
                      WHERE q.status='OPEN' AND s.run_id=? GROUP BY s.match_id,q.role
                    )
                    SELECT m.id,m.tour,m.tourney_date,m.tourney_id,m.round,m.match_num,m.winner_id,m.loser_id,m.surface,m.best_of,m.winner_age,m.loser_age,m.winner_height_cm,m.loser_height_cm,
                      coalesce(w.age_blocked,false) OR coalesce(r.age_blocked,false) AS winner_age_blocked,
                      coalesce(l.age_blocked,false) OR coalesce(r.age_blocked,false) AS loser_age_blocked,
                      coalesce(w.height_blocked,false) OR coalesce(r.height_blocked,false) AS winner_height_blocked,
                      coalesce(l.height_blocked,false) OR coalesce(r.height_blocked,false) AS loser_height_blocked
                    FROM v_stat_matches m LEFT JOIN blocks w ON w.match_id=m.id AND w.role='WINNER'
                    LEFT JOIN blocks l ON l.match_id=m.id AND l.role='LOSER' LEFT JOIN blocks r ON r.match_id=m.id AND r.role='RECORD'
                    WHERE EXISTS(SELECT 1 FROM match_sources s WHERE s.match_id=m.id AND s.run_id=?)
                    ORDER BY m.tour,m.tourney_date,m.tourney_id,CASE m.round WHEN 'RR' THEN 0 WHEN 'ER' THEN 1 WHEN 'R128' THEN 2 WHEN 'R64' THEN 3 WHEN 'R32' THEN 4 WHEN 'R16' THEN 5 WHEN 'QF' THEN 6 WHEN 'SF' THEN 7 WHEN 'BR' THEN 8 WHEN 'F' THEN 9 ELSE 10 END,m.match_num,m.id
                    """;
                try(var writer=Files.newBufferedWriter(csv,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);var printer=new CSVPrinter(writer,CSVFormat.DEFAULT)) {
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");long[] count={0};
                    jdbc.query(query,ps->{ps.setLong(1,source.matchRunId());ps.setLong(2,source.matchRunId());ps.setFetchSize(1000);},(RowCallbackHandler)rs->{
                        try {
                            if(count[0]==0) {List<String> header=new ArrayList<>();for(int i=1;i<=18;i++)header.add(rs.getMetaData().getColumnLabel(i));printer.printRecord(header);}
                            var row=new ArrayList<String>();for(int i=1;i<=18;i++)row.add(rs.getString(i));printer.printRecord(row);count[0]++;
                            for(int i=1;i<=9;i++) {byte[] bytes=(rs.getString(i)==null?"<null>":rs.getString(i)).getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}
                        } catch(java.io.IOException e) {throw new IllegalStateException(e);}
                    });
                    String fingerprint=HexFormat.of().formatHex(digest.digest());
                    if(!fingerprint.equals(model.get("input_sha256")) || count[0]!=((Number)model.get("matches")).longValue()) throw new IllegalStateException("Elo input differs from live eligible matches; rebuild Elo first");
                    output.put("rows",count[0]);output.put("readOnly",true);output.put("eloSamples",jdbc.queryForList("SELECT tour,player_id,dimension,rating,matches FROM elo_current WHERE run_id=? ORDER BY tour,player_id,dimension LIMIT 20",model.get("id")));return output;
                } catch(Exception e) {throw new IllegalStateException("Export failed",e);}
            });
            context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValue(metadata.toFile(),result);
        } catch(Exception e) {Files.deleteIfExists(csv);Files.deleteIfExists(metadata);throw e;}
    }
}
