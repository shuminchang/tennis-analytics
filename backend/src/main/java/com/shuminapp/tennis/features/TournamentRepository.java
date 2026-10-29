package com.shuminapp.tennis.features;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TournamentRepository {
    private final JdbcTemplate jdbc;
    public TournamentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public EventPage events(String tour,String query,Integer year,int page,int size) {
        List<Object> args=new ArrayList<>(List.of(tour));String where=" WHERE m.tour=?";
        if(!query.isBlank()) { where+=" AND m.tourney_name ILIKE ? ESCAPE '!'";args.add("%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%"); }
        if(year!=null) { where+=" AND m.tourney_date>=? AND m.tourney_date<?";args.add(LocalDate.of(year,1,1));args.add(LocalDate.of(year+1,1,1)); }
        String grouped="SELECT m.tour,m.tourney_id,max(m.tourney_name) AS name,max(m.tourney_date) AS date,max(m.surface) AS surface,max(m.tourney_level) AS level,count(*) AS matches FROM v_display_matches m"+where+" GROUP BY m.tour,m.tourney_id";
        long total=jdbc.queryForObject("SELECT count(*) FROM ("+grouped+") e",Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var events=jdbc.query("SELECT e.*,p.player_id AS champion_id,p.name_first,p.name_last FROM ("+grouped+") e LEFT JOIN v_event_final_winners f ON f.tour=e.tour AND f.tourney_id=e.tourney_id LEFT JOIN players p ON p.tour=f.tour AND p.player_id=f.winner_id ORDER BY e.date DESC,e.tourney_id LIMIT ? OFFSET ?",(rs,n)->new Event(rs.getString("tour"),rs.getString("tourney_id"),rs.getString("name"),rs.getDate("date").toLocalDate(),rs.getString("surface"),rs.getString("level"),rs.getLong("matches"),rs.getObject("champion_id")==null?null:new Winner(rs.getInt("champion_id"),rs.getString("name_first"),rs.getString("name_last"))),args.toArray());
        return new EventPage(events,total,page,size);
    }
    public EventDetail detail(String tour,String id,int page,int size) {
        if(jdbc.queryForObject("SELECT count(*) FROM v_display_matches WHERE tour=? AND tourney_id=?",Long.class,tour,id)==0) throw new NoSuchElementException();
        var rows=jdbc.queryForList("SELECT id,tourney_name,tourney_date,round,match_num,winner_id,loser_id,score FROM v_display_matches WHERE tour=? AND tourney_id=? ORDER BY round,match_num,id LIMIT ? OFFSET ?",tour,id,size,(long)page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM v_display_matches WHERE tour=? AND tourney_id=?",Long.class,tour,id);
        return new EventDetail(rows,total,page,size);
    }
    public record Winner(int playerId,String nameFirst,String nameLast) { }
    public record Event(String tour,String tournamentId,String name,LocalDate date,String surface,String sourceLevel,long matches,Winner finalWinner) { }
    public record EventPage(List<Event> items,long total,int page,int size) { }
    public record EventDetail(List<Map<String,Object>> items,long total,int page,int size) { }
}
