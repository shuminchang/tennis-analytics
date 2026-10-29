package com.shuminapp.tennis.features;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EloRepository {
    private final JdbcTemplate jdbc;
    public EloRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public History history(String tour,int id,String dimension) {
        var runs=jdbc.queryForList("SELECT id,rules_sha256,input_sha256,matches,finished_at FROM elo_runs WHERE status='SUCCEEDED' ORDER BY id DESC LIMIT 1");
        if(runs.isEmpty()) return new History(false,null,List.of(),List.of(),0);
        long run=((Number)runs.getFirst().get("id")).longValue();long total=jdbc.queryForObject("SELECT count(*) FROM elo_history WHERE run_id=? AND tour=? AND player_id=? AND dimension=?",Long.class,run,tour,id,dimension);
        var points=jdbc.query("SELECT observed_date,rating_after,sequence FROM elo_history WHERE run_id=? AND tour=? AND player_id=? AND dimension=? ORDER BY sequence LIMIT 5000",(rs,n)->new Point(rs.getDate(1).toLocalDate(),rs.getDouble(2),rs.getInt(3)),run,tour,id,dimension);
        var current=jdbc.queryForList("SELECT dimension,rating,matches,observed_date FROM elo_current WHERE run_id=? AND tour=? AND player_id=? ORDER BY dimension",run,tour,id);
        return new History(true,runs.getFirst(),points,current,total);
    }
    public record Point(LocalDate date,double rating,int sequence) { }
    public record History(boolean available,Map<String,Object> model,List<Point> items,List<Map<String,Object>> current,long total) { }
}
