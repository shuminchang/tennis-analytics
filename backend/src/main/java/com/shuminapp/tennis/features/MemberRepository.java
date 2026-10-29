package com.shuminapp.tennis.features;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {
    private final JdbcTemplate jdbc;
    public MemberRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> favorites(String username) {
        return jdbc.queryForList("SELECT p.tour,p.player_id,p.name_first,p.name_last FROM favorites f JOIN app_users u ON u.id=f.user_id JOIN players p ON p.tour=f.tour AND p.player_id=f.player_id WHERE u.username=? ORDER BY p.tour,p.name_last,p.player_id LIMIT 200",username);
    }
    public void add(String username,String tour,int id) {
        long user=jdbc.queryForObject("SELECT id FROM app_users WHERE username=? FOR UPDATE",Long.class,username);
        boolean exists=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id=? AND tour=? AND player_id=?)",Boolean.class,user,tour,id));
        if(!exists && jdbc.queryForObject("SELECT count(*) FROM favorites WHERE user_id=?",Long.class,user)>=200) throw new IllegalArgumentException("Favorite limit reached");
        jdbc.update("INSERT INTO favorites(user_id,tour,player_id) VALUES(?,?,?) ON CONFLICT DO NOTHING",user,tour,id);
    }
    public void remove(String username,String tour,int id) { jdbc.update("DELETE FROM favorites WHERE user_id=(SELECT id FROM app_users WHERE username=?) AND tour=? AND player_id=?",username,tour,id); }
}
