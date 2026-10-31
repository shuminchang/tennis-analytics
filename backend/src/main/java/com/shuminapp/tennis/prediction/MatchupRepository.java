package com.shuminapp.tennis.prediction;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MatchupRepository {
    private final JdbcTemplate jdbc;
    public MatchupRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public record EloRun(long id,String rulesSha,String inputSha) { }
    public record Rating(double value,int matches,LocalDate date) { }
    public record Observation(double value,LocalDate date) { }
    public record Inputs(String tour,int playerId,String name,Rating overall,Rating surface,Double age,Double height,LocalDate ageDate,LocalDate heightDate) { }
    public EloRun run() {
        if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM elo_runs WHERE status='RUNNING')",Boolean.class))throw new Unavailable();
        return jdbc.query("SELECT id,rules_sha256,input_sha256 FROM elo_runs WHERE status='SUCCEEDED' ORDER BY id DESC LIMIT 1",(rs,n)->new EloRun(rs.getLong(1),rs.getString(2),rs.getString(3))).stream().findFirst().orElseThrow(Unavailable::new);
    }
    public LocalDate latest(EloRun run,String tour) {return jdbc.queryForObject("SELECT max(observed_date) FROM elo_current WHERE run_id=? AND tour=?",LocalDate.class,run.id(),tour);}
    private Rating rating(EloRun run,String tour,int player,String dimension,LocalDate cutoff) {
        return jdbc.query("SELECT rating_after,matches,observed_date FROM elo_history WHERE run_id=? AND tour=? AND player_id=? AND dimension=? AND observed_date<? ORDER BY sequence DESC LIMIT 1",(rs,n)->new Rating(rs.getDouble(1),rs.getInt(2),rs.getDate(3).toLocalDate()),run.id(),tour,player,dimension,cutoff).stream().findFirst().orElse(null);
    }
    public Inputs inputs(EloRun run,String tour,int player,String name,String surface,LocalDate asOf,long matchRun) {
        LocalDate cutoff=asOf.minusDays(21);var overall=rating(run,tour,player,"OVERALL",cutoff);var specific=rating(run,tour,player,surface,cutoff);
        var age=observation(run,tour,player,asOf,true,matchRun);var height=observation(run,tour,player,asOf,false,matchRun);
        Double years=age==null?null:age.value()+java.time.temporal.ChronoUnit.DAYS.between(age.date(),asOf)/365.2425;
        if(years!=null && (years<12 || years>60))years=null;
        return new Inputs(tour,player,name,overall,specific,years,height==null?null:height.value(),age==null?null:age.date(),height==null?null:height.date());
    }
    private Observation observation(EloRun run,String tour,int player,LocalDate asOf,boolean age,long matchRun) {
        String column=age?"age":"height_cm",metric=age?"AGE_ANALYSIS":"HEIGHT_ANALYSIS";
        String sql="""
            SELECT CASE WHEN m.winner_id=h.player_id THEN m.winner_%s ELSE m.loser_%s END AS value,h.observed_date
            FROM elo_history h JOIN matches m ON m.id=h.match_id
            WHERE h.run_id=? AND h.tour=? AND h.player_id=? AND h.dimension='OVERALL'
            AND h.observed_date<? AND h.observed_date>=?
            AND CASE WHEN m.winner_id=h.player_id THEN m.winner_%s ELSE m.loser_%s END BETWEEN %s
            AND NOT EXISTS (
              SELECT 1 FROM match_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
              JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.match_id=m.id AND s.run_id=? AND q.status='OPEN' AND i.metric_code=?
              AND q.role IN ('RECORD',CASE WHEN m.winner_id=h.player_id THEN 'WINNER' ELSE 'LOSER' END)
            ) ORDER BY h.sequence DESC LIMIT 1
            """.formatted(column,column,column,column,age?"12 AND 60":"100 AND 250");
        return jdbc.query(sql,(rs,n)->new Observation(rs.getDouble(1),rs.getDate(2).toLocalDate()),run.id(),tour,player,asOf.minusDays(21),asOf.minusDays(365),matchRun,metric).stream().findFirst().orElse(null);
    }
    public static final class Unavailable extends RuntimeException { }
}
