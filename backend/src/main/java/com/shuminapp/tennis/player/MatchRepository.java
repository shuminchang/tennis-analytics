package com.shuminapp.tennis.player;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MatchRepository {
    private final JdbcTemplate jdbc;
    public MatchRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public History history(String tour,int playerId,Integer year,int page,int size) {
        boolean available=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM import_runs WHERE data_kind='MATCHES' AND status='SUCCEEDED')",Boolean.class));
        List<Object> args=new ArrayList<>(List.of(tour,playerId,playerId));
        String where="""
                FROM matches m JOIN players opponent ON opponent.tour=m.tour
                    AND opponent.player_id=CASE WHEN m.winner_id=? THEN m.loser_id ELSE m.winner_id END
                WHERE m.tour=? AND (m.winner_id=? OR m.loser_id=?)
                  AND EXISTS(SELECT 1 FROM match_sources s JOIN import_runs r ON r.id=s.run_id
                    JOIN tournament_decisions d ON d.run_id=r.id AND d.tour=m.tour AND d.tourney_id=m.tourney_id
                    WHERE s.match_id=m.id AND s.tour=m.tour AND r.status='SUCCEEDED' AND r.data_kind='MATCHES' AND d.scope_status='INCLUDE')
                  AND NOT EXISTS(SELECT 1 FROM match_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
                    JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.match_id=m.id AND s.tour=m.tour AND q.status='OPEN' AND i.metric_code='MATCH_HISTORY')
                """;
        args.addFirst(playerId);
        if(year!=null) {
            where+=" AND m.tourney_date>=? AND m.tourney_date<?";
            args.add(LocalDate.of(year,1,1));args.add(LocalDate.of(year+1,1,1));
        }
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var items=jdbc.query("""
                SELECT m.id,m.tourney_id,m.tourney_name,m.tourney_date,m.tourney_level,m.surface,m.round,m.match_num,m.score,
                    m.winner_id,opponent.player_id,opponent.name_first,opponent.name_last,
                    EXISTS(SELECT 1 FROM match_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
                        JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.match_id=m.id AND s.tour=m.tour AND q.status='OPEN' AND i.metric_code='SCORE_ANALYSIS') AS score_review,
                    NOT EXISTS(SELECT 1 FROM player_sources ps JOIN quality_issues q ON q.run_id=ps.run_id AND q.source_file_id=ps.source_file_id AND q.record_number=ps.record_number
                        JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE ps.tour=opponent.tour AND ps.player_id=opponent.player_id AND q.status='OPEN' AND i.metric_code='PLAYER_SEARCH') AS opponent_visible
                """+where+" ORDER BY m.tourney_date DESC,m.tourney_id,m.round,m.match_num,m.id LIMIT ? OFFSET ?",(rs,n)->{
                    String score=rs.getString("score"),status=scoreStatus(score);boolean winner=rs.getInt("winner_id")==playerId;
                    String result=status.equals("WALKOVER")?(winner?"ADVANCED":"NOT_PLAYED"):
                            Set.of("UNKNOWN","MISSING_SCORE").contains(status)?(winner?"SOURCE_WINNER":"SOURCE_LOSER"):(winner?"WIN":"LOSS");
                    boolean visible=rs.getBoolean("opponent_visible");
                    return new Entry(rs.getLong("id"),rs.getString("tourney_id"),rs.getString("tourney_name"),rs.getDate("tourney_date").toLocalDate(),rs.getString("tourney_level"),
                            rs.getString("surface"),rs.getString("round"),rs.getInt("match_num"),score,rs.getBoolean("score_review"),status,result,
                            new Opponent(rs.getInt("player_id"),visible?rs.getString("name_first"):null,visible?rs.getString("name_last"):null,visible));
                },args.toArray());
        return new History(items,total,page,size,available);
    }
    private String scoreStatus(String score) {
        if(score==null || score.isBlank()) return "MISSING_SCORE";
        String text=score.toUpperCase(Locale.ROOT);
        if(text.contains("W/O")) return "WALKOVER";
        if(text.contains("RET")) return "RETIRED";
        if(text.contains("DEF")) return "DEFAULTED";
        if(text.contains("UNK")) return "UNKNOWN";
        return "RECORDED";
    }
    public record Opponent(int playerId,String nameFirst,String nameLast,boolean visible) { }
    public record Entry(long id,String tournamentId,String tournamentName,LocalDate tournamentDate,String sourceLevel,String surface,String round,int matchNumber,String score,boolean scoreNeedsReview,String status,String result,Opponent opponent) { }
    public record History(List<Entry> items,long total,int page,int size,boolean available) { public History { items=List.copyOf(items); } }
}
