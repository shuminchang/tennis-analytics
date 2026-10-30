package com.shuminapp.tennis.visualization;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.shuminapp.tennis.visualization.ResearchDtos.*;

@Repository
public class ResearchRepository {
    private final JdbcTemplate jdbc;private final VisualizationRepository published;
    public ResearchRepository(JdbcTemplate jdbc,VisualizationRepository published) { this.jdbc=jdbc;this.published=published; }
    public Source source() {
        var base=published.source();
        if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM import_runs WHERE data_kind='MATCHES' AND status='RUNNING')",Boolean.class)) throw new VisualizationRepository.NotReady();
        var rows=jdbc.query("""
            SELECT r.id,s.commit_sha,r.transform_rules_sha256,r.quality_rules_sha256,r.scope_rules_sha256
            FROM import_runs r JOIN source_snapshots s ON s.id=r.snapshot_id
            WHERE r.status='SUCCEEDED' AND r.data_kind='MATCHES' ORDER BY r.id
            """,(rs,n)->new MatchRun(rs.getLong(1),rs.getString(2),new VisualizationDtos.Rules(rs.getString(3),rs.getString(4),rs.getString(5))));
        if(rows.isEmpty() || rows.stream().anyMatch(r->!r.sha.equals(base.snapshotSha())) || rows.stream().map(MatchRun::rules).distinct().count()!=1) throw new VisualizationRepository.NotReady();
        var last=rows.getLast();
        String revision=jdbc.queryForObject("""
            SELECT md5(coalesce(string_agg(q.id::text||':'||q.status||':'||q.role||':'||i.metric_code,',' ORDER BY q.id,i.metric_code),''))
            FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id JOIN import_runs r ON r.id=q.run_id WHERE r.status='SUCCEEDED'
            """,String.class);
        return new Source(base,last.id,last.rules,revision,"v_stat_matches-live");
    }
    private record MatchRun(long id,String sha,VisualizationDtos.Rules rules) { }
    public Info info(Source source,String tour) {
        return jdbc.queryForObject("SELECT extract(year from min(tourney_date))::integer,extract(year from max(tourney_date))::integer FROM matches WHERE tour=?",(rs,n)->new Info(source,(Integer)rs.getObject(1),(Integer)rs.getObject(2),10,1000,List.of("TOURNAMENT_DATE_APPROXIMATION","PARTIAL_SOURCE_COVERAGE")),tour);
    }
    public record Query(String sql,Object[] args) { }
    public Query sides(Source source,ResearchRequest r) {
        String sql="""
            WITH blocks AS MATERIALIZED (
                SELECT s.match_id,q.role,bool_or(i.metric_code='AGE_ANALYSIS') AS age_blocked,bool_or(i.metric_code='SERVE_STATS') AS serve_blocked
                FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id
                JOIN match_sources s ON s.run_id=q.run_id AND s.source_file_id=q.source_file_id AND s.record_number=q.record_number
                WHERE q.status='OPEN' AND s.run_id=? GROUP BY s.match_id,q.role
            ), sides AS (
                SELECT m.id,m.tour,extract(year from m.tourney_date)::integer AS season,coalesce(m.surface,'Unknown') AS surface,v.*,
                    coalesce(b.age_blocked,false) OR coalesce(br.age_blocked,false) AS age_blocked,
                    coalesce(b.serve_blocked,false) OR coalesce(br.serve_blocked,false) AS serve_blocked
                FROM v_stat_matches m CROSS JOIN LATERAL (VALUES
                    ('WINNER',m.winner_id,m.loser_id,true,m.winner_age,m.w_ace,m.w_df,m.w_svpt,m.w_1st_in,m.w_1st_won,m.w_2nd_won),
                    ('LOSER',m.loser_id,m.winner_id,false,m.loser_age,m.l_ace,m.l_df,m.l_svpt,m.l_1st_in,m.l_1st_won,m.l_2nd_won)
                ) v(role,player_id,opponent_id,won,age,ace,df,svpt,first_in,first_won,second_won)
                LEFT JOIN blocks b ON b.match_id=m.id AND b.role=v.role LEFT JOIN blocks br ON br.match_id=m.id AND br.role='RECORD'
                WHERE m.tour=? AND m.tourney_date>=? AND m.tourney_date<? AND (?='ALL' OR coalesce(m.surface,'Unknown')=?)
                AND EXISTS(SELECT 1 FROM match_sources ms WHERE ms.match_id=m.id AND ms.tour=m.tour AND ms.run_id=?)
            )
            """;
        return new Query(sql,new Object[]{source.matchRunId(),r.tour(),LocalDate.of(r.fromYear(),1,1),LocalDate.of(r.toYear()+1,1,1),r.surface(),r.surface(),source.matchRunId()});
    }
    public record AgeAggregate(Double bucket,String surface,String reason,long n,long wins) { }
    public List<AgeAggregate> ages(Source source,ResearchRequest r) {
        var query=sides(source,r);String group=r.group().equals("surface")?"surface":"'ALL'";
        String sql=query.sql()+", classified AS (SELECT *,CASE WHEN age_blocked THEN 'AGE_QUALITY_BLOCKED' WHEN age IS NULL THEN 'AGE_MISSING' WHEN age<=0 THEN 'AGE_NONPOSITIVE' ELSE 'VALID' END AS reason FROM sides)\n"+
            "SELECT CASE WHEN reason='VALID' THEN floor(age/"+r.binWidth()+")*"+r.binWidth()+" ELSE NULL END AS bucket,"+group+" AS surface,reason,count(*),count(*) FILTER(WHERE won) FROM classified GROUP BY 1,2,3 ORDER BY 1,2,3";
        return jdbc.query(sql,(rs,n)->new AgeAggregate(rs.getObject(1)==null?null:rs.getDouble(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5)),query.args());
    }
}
