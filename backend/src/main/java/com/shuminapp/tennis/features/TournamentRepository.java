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
        List<Object> args=new ArrayList<>(List.of(tour));String where=" WHERE e.tour=?";
        if(!query.isBlank()) { where+=" AND e.name ILIKE ? ESCAPE '!'";args.add("%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%"); }
        if(year!=null) { where+=" AND e.date>=? AND e.date<?";args.add(LocalDate.of(year,1,1));args.add(LocalDate.of(year+1,1,1)); }
        long total=jdbc.queryForObject("SELECT count(*) FROM analytics_events e"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var events=jdbc.query("SELECT e.*,p.name_first,p.name_last FROM analytics_events e LEFT JOIN players p ON p.tour=e.tour AND p.player_id=e.champion_id"+where+" ORDER BY e.date DESC,e.tourney_id LIMIT ? OFFSET ?",(rs,n)->new Event(rs.getString("tour"),rs.getString("tourney_id"),rs.getString("name"),rs.getDate("date").toLocalDate(),rs.getString("surface"),rs.getString("level"),rs.getLong("matches"),rs.getObject("champion_id")==null?null:new Winner(rs.getInt("champion_id"),rs.getString("name_first"),rs.getString("name_last"))),args.toArray());
        return new EventPage(events,total,page,size);
    }
    public EventDetail detail(String tour,String id,int page,int size) {
        if(jdbc.queryForObject("SELECT count(*) FROM analytics_matches WHERE tour=? AND tourney_id=?",Long.class,tour,id)==0) throw new NoSuchElementException();
        var rows=jdbc.queryForList("SELECT id,tourney_name,tourney_date,round,match_num,winner_id,loser_id,score FROM analytics_matches WHERE tour=? AND tourney_id=? ORDER BY round,match_num,id LIMIT ? OFFSET ?",tour,id,size,(long)page*size);
        long total=jdbc.queryForObject("SELECT count(*) FROM analytics_matches WHERE tour=? AND tourney_id=?",Long.class,tour,id);
        return new EventDetail(rows,total,page,size);
    }
    public record Winner(int playerId,String nameFirst,String nameLast) { }
    public record Event(String tour,String tournamentId,String name,LocalDate date,String surface,String sourceLevel,long matches,Winner finalWinner) { }
    public record EventPage(List<Event> items,long total,int page,int size) { }
    public record EventDetail(List<Map<String,Object>> items,long total,int page,int size) { }
}
