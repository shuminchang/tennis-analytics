package com.shuminapp.tennis.features;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RankingBrowseRepository {
    private final JdbcTemplate jdbc;
    public RankingBrowseRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Board board(String tour,LocalDate date,int size) {
        if(date==null) date=jdbc.query("SELECT ranking_date FROM v_public_rankings WHERE tour=? ORDER BY ranking_date DESC LIMIT 1",(rs,n)->rs.getDate(1).toLocalDate(),tour).stream().findFirst().orElse(null);
        if(date==null) return new Board(null,List.of());
        var rows=jdbc.query("SELECT r.rank,r.points,r.player_id,p.name_first,p.name_last FROM v_public_rankings r JOIN players p ON p.tour=r.tour AND p.player_id=r.player_id WHERE r.tour=? AND r.ranking_date=? ORDER BY r.rank,r.player_id LIMIT ?",(rs,n)->new Ranked(rs.getInt("player_id"),rs.getString("name_first"),rs.getString("name_last"),rs.getInt("rank"),(Integer)rs.getObject("points")),tour,date,size);
        return new Board(date,rows);
    }
    public Chart chart(String tour,int id) {
        long total=jdbc.queryForObject("SELECT count(*) FROM v_public_rankings WHERE tour=? AND player_id=?",Long.class,tour,id);
        var rows=jdbc.query("SELECT ranking_date,rank,points FROM v_public_rankings WHERE tour=? AND player_id=? ORDER BY ranking_date LIMIT 5000",(rs,n)->new Point(rs.getDate(1).toLocalDate(),rs.getInt(2),(Integer)rs.getObject(3)),tour,id);
        return new Chart(rows,total);
    }
    public List<Map<String,Object>> imports() { return jdbc.queryForList("SELECT data_kind,max(finished_at) AS imported_at FROM import_runs WHERE status='SUCCEEDED' GROUP BY data_kind ORDER BY data_kind"); }
    public record Ranked(int playerId,String nameFirst,String nameLast,int rank,Integer points) { }
    public record Board(LocalDate date,List<Ranked> items) { }
    public record Point(LocalDate date,int rank,Integer points) { }
    public record Chart(List<Point> items,long total) { }
}
