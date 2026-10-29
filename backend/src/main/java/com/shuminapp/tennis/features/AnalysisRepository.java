package com.shuminapp.tennis.features;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepository {
    private final JdbcTemplate jdbc;
    public AnalysisRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Breakdown> breakdown(String tour,int id,String by) {
        String key=by.equals("year")?"extract(year from m.tourney_date)::integer::text":"coalesce(m.surface,'Unknown')";
        String sql="""
                WITH samples AS (
                    SELECT %s AS bucket,m.winner_id=? AS won,
                        CASE WHEN m.winner_id=? THEN m.w_svpt ELSE m.l_svpt END AS svpt,
                        CASE WHEN m.winner_id=? THEN m.w_1st_won+m.w_2nd_won ELSE m.l_1st_won+m.l_2nd_won END AS points_won,
                        CASE WHEN m.winner_id=? THEN m.w_ace ELSE m.l_ace END AS ace,
                        CASE WHEN m.winner_id=? THEN m.w_df ELSE m.l_df END AS df,
                        NOT EXISTS(SELECT 1 FROM match_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
                            JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.match_id=m.id AND q.status='OPEN' AND i.metric_code='SERVE_STATS'
                                AND q.role IN ('RECORD',CASE WHEN m.winner_id=? THEN 'WINNER' ELSE 'LOSER' END)) AS valid
                    FROM analytics_matches m WHERE m.eligible AND m.tour=? AND (m.winner_id=? OR m.loser_id=?)
                )
                SELECT bucket,count(*) AS matches,count(*) FILTER(WHERE won) AS wins,
                    count(*) FILTER(WHERE valid AND svpt>0 AND points_won>=0) AS serve_samples,
                    sum(points_won) FILTER(WHERE valid AND svpt>0 AND points_won>=0)::double precision/nullif(sum(svpt) FILTER(WHERE valid AND svpt>0 AND points_won>=0),0) AS serve_rate,
                    count(*) FILTER(WHERE valid AND svpt>0 AND ace>=0) AS ace_samples,
                    sum(ace) FILTER(WHERE valid AND svpt>0 AND ace>=0)::double precision/nullif(sum(svpt) FILTER(WHERE valid AND svpt>0 AND ace>=0),0) AS ace_rate,
                    count(*) FILTER(WHERE valid AND svpt>0 AND df>=0) AS df_samples,
                    sum(df) FILTER(WHERE valid AND svpt>0 AND df>=0)::double precision/nullif(sum(svpt) FILTER(WHERE valid AND svpt>0 AND df>=0),0) AS df_rate
                FROM samples GROUP BY bucket ORDER BY bucket
                """.formatted(key);
        return jdbc.query(sql,(rs,n)->new Breakdown(rs.getString("bucket"),rs.getLong("matches"),rs.getLong("wins"),rs.getLong("serve_samples"),(Double)rs.getObject("serve_rate"),rs.getLong("ace_samples"),(Double)rs.getObject("ace_rate"),rs.getLong("df_samples"),(Double)rs.getObject("df_rate")),id,id,id,id,id,id,tour,id,id);
    }
    public record Breakdown(String bucket,long matches,long wins,long serveSamples,Double serveRate,long aceSamples,Double aceRate,long dfSamples,Double dfRate) { }
}
