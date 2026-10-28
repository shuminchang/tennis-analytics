package com.shuminapp.tennis.player;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerRepository {
    private final JdbcTemplate jdbc;
    public PlayerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String VISIBLE_PLAYERS = """
                FROM players p
                WHERE EXISTS (
                    SELECT 1 FROM player_sources ps JOIN import_runs r ON r.id=ps.run_id
                    WHERE ps.tour=p.tour AND ps.player_id=p.player_id AND r.status='SUCCEEDED'
                ) AND NOT EXISTS (
                    SELECT 1 FROM player_sources ps
                    JOIN import_runs r ON r.id=ps.run_id
                    JOIN quality_issues qi ON qi.run_id=ps.run_id AND qi.source_file_id=ps.source_file_id
                        AND qi.record_number=ps.record_number
                    JOIN quality_issue_impacts impact ON impact.issue_id=qi.id
                    WHERE ps.tour=p.tour AND ps.player_id=p.player_id AND r.status='SUCCEEDED'
                        AND qi.status='OPEN' AND impact.metric_code='PLAYER_SEARCH'
                )
                """;

    public SearchPage search(String query, String tour, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = VISIBLE_PLAYERS;
        if (tour != null) { where += " AND p.tour=?"; args.add(tour); }
        if (!query.isEmpty()) {
            where += " AND concat_ws(' ',p.name_first,p.name_last) ILIKE ? ESCAPE '!'";
            args.add("%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long total = jdbc.queryForObject("SELECT count(*) " + where, Long.class, args.toArray());
        args.add(size); args.add((long)page * size);
        var items = jdbc.query("""
                SELECT p.tour,p.player_id,p.name_first,p.name_last,p.hand,p.dob,p.ioc
                """ + where + " ORDER BY lower(coalesce(p.name_last,'')),lower(coalesce(p.name_first,'')),p.tour,p.player_id LIMIT ? OFFSET ?",
                (rs, row) -> {
                    Date dob = rs.getDate("dob");
                    return new PlayerSummary(rs.getString("tour"), rs.getInt("player_id"),
                            rs.getString("name_first"), rs.getString("name_last"), rs.getString("hand"),
                            dob == null ? null : dob.toLocalDate(), rs.getString("ioc"));
                }, args.toArray());
        return new SearchPage(items,total,page,size);
    }
    public Optional<PlayerSummary> findById(String tour,int playerId) {
        var players = jdbc.query("""
                SELECT p.tour,p.player_id,p.name_first,p.name_last,p.hand,p.dob,p.ioc
                """ + VISIBLE_PLAYERS + " AND p.tour=? AND p.player_id=?",
                (rs, row) -> {
                    Date dob = rs.getDate("dob");
                    return new PlayerSummary(rs.getString("tour"), rs.getInt("player_id"),
                            rs.getString("name_first"), rs.getString("name_last"), rs.getString("hand"),
                            dob == null ? null : dob.toLocalDate(), rs.getString("ioc"));
                }, tour,playerId);
        return players.stream().findFirst();
    }
    public record PlayerSummary(String tour,int playerId,String nameFirst,String nameLast,String hand,LocalDate dob,String ioc) { }
    public record SearchPage(List<PlayerSummary> items,long total,int page,int size) {
        public SearchPage { items = List.copyOf(items); }
    }
}
