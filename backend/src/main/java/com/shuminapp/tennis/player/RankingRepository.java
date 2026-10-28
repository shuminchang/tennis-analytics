package com.shuminapp.tennis.player;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RankingRepository {
    private final JdbcTemplate jdbc;
    public RankingRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public History history(String tour,int playerId,int page,int size) {
        boolean available=Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM import_runs WHERE data_kind='RANKINGS' AND status='SUCCEEDED')",Boolean.class));
        String where="""
                FROM rankings r WHERE r.tour=? AND r.player_id=? AND EXISTS (
                    SELECT 1 FROM ranking_sources s JOIN import_runs i ON i.id=s.run_id
                    WHERE s.tour=r.tour AND s.ranking_date=r.ranking_date AND s.player_id=r.player_id
                        AND i.data_kind='RANKINGS' AND i.status='SUCCEEDED'
                )
                """;
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,tour,playerId);
        var rows=jdbc.query("SELECT r.ranking_date,r.rank,r.points "+where+" ORDER BY r.ranking_date DESC LIMIT ? OFFSET ?",
                (rs,n)->new Entry(rs.getDate("ranking_date").toLocalDate(),rs.getInt("rank"),(Integer)rs.getObject("points")),tour,playerId,size,(long)page*size);
        return new History(rows,total,page,size,available);
    }
    public record Entry(LocalDate date,int rank,Integer points) { }
    public record History(List<Entry> items,long total,int page,int size,boolean available) {
        public History { items=List.copyOf(items); }
    }
}
