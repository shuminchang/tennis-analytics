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
            SELECT r.id,r.snapshot_id,s.commit_sha,r.transform_rules_sha256,r.quality_rules_sha256,r.scope_rules_sha256
            FROM import_runs r JOIN source_snapshots s ON s.id=r.snapshot_id
            WHERE r.status='SUCCEEDED' AND r.data_kind='MATCHES' ORDER BY r.id
            """,(rs,n)->new MatchRun(rs.getLong(1),rs.getLong(2),rs.getString(3),new VisualizationDtos.Rules(rs.getString(4),rs.getString(5),rs.getString(6))));
        long snapshot=jdbc.queryForObject("SELECT snapshot_id FROM import_runs WHERE id=?",Long.class,base.playerRunId());
        if(rows.isEmpty() || rows.stream().anyMatch(r->r.snapshot!=snapshot) || rows.stream().anyMatch(r->!r.sha.equals(base.snapshotSha())) || rows.stream().map(MatchRun::rules).distinct().count()!=1) throw new VisualizationRepository.NotReady();
        var last=rows.getLast();
        String revision=jdbc.queryForObject("""
            SELECT md5(coalesce(string_agg(q.id::text||':'||q.status||':'||q.role||':'||i.metric_code,',' ORDER BY q.id,i.metric_code),''))
            FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id JOIN import_runs r ON r.id=q.run_id WHERE r.status='SUCCEEDED'
            """,String.class);
        return new Source(base,last.id,last.rules,revision,"v_stat_matches-live");
    }
    private record MatchRun(long id,long snapshot,String sha,VisualizationDtos.Rules rules) { }
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
    private Query enriched(Source source,ResearchRequest r) {
        var sides=sides(source,r);
        String sql=sides.sql()+"""
            , player_blocks AS MATERIALIZED (
                SELECT ps.tour,ps.player_id,bool_or(i.metric_code='HEIGHT_ANALYSIS') AS height_blocked,bool_or(i.metric_code='PLAYER_SEARCH') AS hidden
                FROM player_sources ps JOIN quality_issues q ON q.run_id=ps.run_id AND q.source_file_id=ps.source_file_id AND q.record_number=ps.record_number
                JOIN quality_issue_impacts i ON i.issue_id=q.id JOIN import_runs ir ON ir.id=ps.run_id AND ir.status='SUCCEEDED'
                WHERE q.status='OPEN' GROUP BY ps.tour,ps.player_id
            ), enriched AS (
                SELECT s.*,concat_ws(' ',p.name_first,p.name_last) AS name,p.height_cm,coalesce(p.hand,'UNKNOWN') AS hand,coalesce(op.hand,'UNKNOWN') AS opponent_hand,
                    CASE WHEN coalesce(pb.height_blocked,false) THEN 'QUALITY_BLOCKED' WHEN p.height_cm IS NULL THEN 'MISSING' WHEN p.height_cm NOT BETWEEN 100 AND 250 THEN 'OUT_OF_RANGE' ELSE 'VALID' END AS height_reason,
                    NOT serve_blocked AND svpt>0 AND ace BETWEEN 0 AND svpt AS ace_ok,
                    NOT serve_blocked AND svpt>0 AND df BETWEEN 0 AND svpt AS df_ok,
                    NOT serve_blocked AND first_in>0 AND first_won BETWEEN 0 AND first_in AND (svpt IS NULL OR first_in<=svpt) AS first_ok,
                    NOT serve_blocked AND svpt>0 AND first_won>=0 AND second_won>=0 AND first_won::bigint+second_won<=svpt AS serve_ok
                FROM sides s JOIN players p ON p.tour=s.tour AND p.player_id=s.player_id
                JOIN players op ON op.tour=s.tour AND op.player_id=s.opponent_id
                LEFT JOIN player_blocks pb ON pb.tour=p.tour AND pb.player_id=p.player_id
                WHERE NOT coalesce(pb.hidden,false)
            )
            """;
        return new Query(sql,sides.args());
    }
    private static final String TOTALS="""
        count(*) AS matches,count(*) FILTER(WHERE won) AS wins,
        count(*) FILTER(WHERE ace_ok) AS an,coalesce(sum(ace) FILTER(WHERE ace_ok),0) AS anum,coalesce(sum(svpt) FILTER(WHERE ace_ok),0) AS aden,
        count(*) FILTER(WHERE df_ok) AS dn,coalesce(sum(df) FILTER(WHERE df_ok),0) AS dnum,coalesce(sum(svpt) FILTER(WHERE df_ok),0) AS dden,
        count(*) FILTER(WHERE first_ok) AS fn,coalesce(sum(first_won) FILTER(WHERE first_ok),0) AS fnum,coalesce(sum(first_in) FILTER(WHERE first_ok),0) AS fden,
        count(*) FILTER(WHERE serve_ok) AS sn,coalesce(sum(first_won::bigint+second_won) FILTER(WHERE serve_ok),0) AS snum,coalesce(sum(svpt) FILTER(WHERE serve_ok),0) AS sden
        """;
    public record ProfileRows(List<Profile> items,long population) { }
    public ProfileRows profiles(Source source,ResearchRequest r) {
        var q=enriched(source,r);var args=new ArrayList<>(Arrays.asList(q.args()));args.add(r.limit());
        String sql=q.sql()+"SELECT player_id,name,season,surface,hand,height_cm,height_reason,"+TOTALS+",count(*) OVER() AS population FROM enriched GROUP BY player_id,name,season,surface,hand,height_cm,height_reason ORDER BY matches DESC,player_id,season,surface LIMIT ?";
        long[] total={0};var items=jdbc.query(sql,(rs,n)->{ total[0]=rs.getLong("population");var measures=measures(rs);
            Double height=rs.getObject("height_cm")==null?null:rs.getDouble("height_cm");boolean valid=rs.getString("height_reason").equals("VALID");
            measures.put("height_cm",new Measure(valid?height:null,valid?1:0,0,0,valid));
            return new Profile(r.tour(),rs.getInt("player_id"),rs.getString("name"),rs.getInt("season"),rs.getString("surface"),rs.getString("hand"),rs.getLong("matches"),Collections.unmodifiableMap(measures),rs.getString("height_reason"));
        },args.toArray());return new ProfileRows(List.copyOf(items),total[0]);
    }
    public List<Slice> slices(Source source,ResearchRequest r,boolean hands) {
        var q=enriched(source,r);String dims=hands?"'ALL' AS season,surface,hand,opponent_hand":"season::text,surface,'ALL' AS hand,'ALL' AS opponent_hand";
        return jdbc.query(q.sql()+"SELECT "+dims+","+TOTALS+" FROM enriched GROUP BY 1,2,3,4 ORDER BY 1,2,3,4",(rs,n)->new Slice(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong("matches"),Collections.unmodifiableMap(measures(rs))),q.args());
    }
    private static LinkedHashMap<String,Measure> measures(java.sql.ResultSet rs) throws java.sql.SQLException {
        var out=new LinkedHashMap<String,Measure>();long n=rs.getLong("matches"),wins=rs.getLong("wins");
        out.put("match_win_rate",new Measure(n==0?null:(double)wins/n,n,wins,n,n>=10));
        for(var metric:List.of(new String[]{"ace_rate","a"},new String[]{"double_fault_rate","d"},new String[]{"first_serve_points_won","f"},new String[]{"serve_points_won","s"})) {
            String p=metric[1];long count=rs.getLong(p+"n"),num=rs.getLong(p+"num"),den=rs.getLong(p+"den");
            out.put(metric[0],new Measure(den==0?null:(double)num/den,count,num,den,count>=5 && den>=(p.equals("f")?50:100)));
        }
        return out;
    }

}
