package com.shuminapp.tennis.features;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public class StatsRepository {
    private final JdbcTemplate jdbc;
    public StatsRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Summary summary(String tour,int id) {
        long displayed=jdbc.queryForObject("SELECT count(*) FROM v_display_matches WHERE tour=? AND (winner_id=? OR loser_id=?)",Long.class,tour,id,id);
        return jdbc.queryForObject("SELECT count(*) AS played,count(*) FILTER(WHERE winner_id=?) AS wins,min(tourney_date) AS first,max(tourney_date) AS last FROM v_stat_matches WHERE tour=? AND (winner_id=? OR loser_id=?)",(rs,n)->{
            long played=rs.getLong("played"),wins=rs.getLong("wins");
            return new Summary("stats-v1",displayed,played,wins,played-wins,played==0?null:(double)wins/played,
                    rs.getDate("first")==null?null:rs.getDate("first").toLocalDate(),rs.getDate("last")==null?null:rs.getDate("last").toLocalDate());
        },id,tour,id,id);
    }
    public record Summary(String ruleVersion,long displayed,long played,long wins,long losses,Double winRate,LocalDate firstDate,LocalDate lastDate) { }
}
