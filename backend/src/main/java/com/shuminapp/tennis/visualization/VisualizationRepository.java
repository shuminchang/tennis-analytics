package com.shuminapp.tennis.visualization;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.shuminapp.tennis.visualization.VisualizationDtos.*;

@Repository
public class VisualizationRepository {
    private final JdbcTemplate jdbc;
    public VisualizationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public static final class NotReady extends RuntimeException { }
    private record Run(long id,long snapshot,String kind,String url,String sha,Rules rules) { }
    public Source source() {
        jdbc.execute("SET LOCAL statement_timeout='5s'");jdbc.execute("SET LOCAL jit=off");
        if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM import_runs WHERE data_kind IN ('PLAYERS','RANKINGS') AND status='RUNNING')",Boolean.class)) throw new NotReady();
        var runs=jdbc.query("""
            SELECT r.*,s.repository_url,s.commit_sha FROM import_runs r JOIN source_snapshots s ON s.id=r.snapshot_id
            WHERE r.status='SUCCEEDED' AND r.data_kind IN ('PLAYERS','RANKINGS') ORDER BY r.id
            """,(rs,n)->new Run(rs.getLong("id"),rs.getLong("snapshot_id"),rs.getString("data_kind"),rs.getString("repository_url"),rs.getString("commit_sha"),new Rules(rs.getString("transform_rules_sha256"),rs.getString("quality_rules_sha256"),rs.getString("scope_rules_sha256"))));
        if(runs.stream().map(Run::snapshot).distinct().count()!=1) throw new NotReady();
        Map<String,Run> latest=new HashMap<>();
        for(var run:runs) { var old=latest.put(run.kind,run);if(old!=null && !old.rules.equals(run.rules)) throw new NotReady(); }
        var p=latest.get("PLAYERS");var r=latest.get("RANKINGS");if(p==null || r==null) throw new NotReady();
        String revision=jdbc.queryForObject("""
            SELECT md5(coalesce(string_agg(q.id::text||':'||q.status||':'||i.metric_code,',' ORDER BY q.id,i.metric_code),''))
            FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id
            JOIN import_runs qr ON qr.id=q.run_id
            WHERE qr.status='SUCCEEDED' AND qr.data_kind IN ('PLAYERS','RANKINGS')
            """,String.class);
        return new Source(p.url,p.sha,p.id,r.id,null,p.rules,r.rules,revision);
    }
    private static final String PUBLIC = """
        FROM v_public_rankings r JOIN players p ON p.tour=r.tour AND p.player_id=r.player_id
        WHERE r.tour=? AND EXISTS(SELECT 1 FROM ranking_sources s WHERE s.tour=r.tour AND s.player_id=r.player_id AND s.ranking_date=r.ranking_date AND s.run_id=?)
        AND EXISTS(SELECT 1 FROM player_sources s WHERE s.tour=p.tour AND s.player_id=p.player_id AND s.run_id=?)
        """;
    public LocalDate boundary(Source s,String tour,boolean latest) {
        String order=latest?"DESC":"ASC";
        return jdbc.query("SELECT r.ranking_date "+PUBLIC+" ORDER BY r.ranking_date "+order+" LIMIT 1",(rs,n)->rs.getDate(1).toLocalDate(),tour,s.rankingRunId(),s.playerRunId()).stream().findFirst().orElse(null);
    }
    public List<Row> cohort(Source s,String tour,LocalDate date,int topN) {
        if(date==null) return List.of();
        return jdbc.query("""
            SELECT p.player_id,concat_ws(' ',p.name_first,p.name_last) AS name,p.height_cm,p.hand,r.rank,r.points,
            EXISTS(SELECT 1 FROM player_sources ps JOIN quality_issues q ON q.run_id=ps.run_id AND q.source_file_id=ps.source_file_id AND q.record_number=ps.record_number
                JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE ps.tour=p.tour AND ps.player_id=p.player_id AND ps.run_id=? AND q.status='OPEN' AND i.metric_code='HEIGHT_ANALYSIS') AS blocked
            """+PUBLIC+" AND r.ranking_date=? AND r.rank<=? ORDER BY r.rank,r.player_id LIMIT 2001",
            (rs,n)->new Row(rs.getInt("player_id"),rs.getString("name"),(Integer)rs.getObject("height_cm"),rs.getString("hand"),rs.getInt("rank"),(Integer)rs.getObject("points"),rs.getBoolean("blocked")),
            s.playerRunId(),tour,s.rankingRunId(),s.playerRunId(),date,topN);
    }
    public record Row(int playerId,String name,Integer height,String hand,int rank,Integer points,boolean blocked) { }
}
