package com.shuminapp.tennis.auth;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<User> find(String username,boolean lock) {
        return jdbc.query("SELECT * FROM app_users WHERE username=?"+(lock?" FOR UPDATE":""),(rs,n)->new User(rs.getLong("id"),rs.getString("username"),rs.getString("password_hash"),rs.getString("role"),rs.getBoolean("enabled"),rs.getInt("failed_attempts"),rs.getTimestamp("locked_until")==null?null:rs.getTimestamp("locked_until").toInstant()),username).stream().findFirst();
    }
    public long create(String username,String hash,String role) { return jdbc.queryForObject("INSERT INTO app_users(username,password_hash,role) VALUES(?,?,?) RETURNING id",Long.class,username,hash,role); }
    public void failed(User user) { jdbc.update("UPDATE app_users SET failed_attempts=CASE WHEN locked_until IS NOT NULL AND locked_until<=clock_timestamp() THEN 1 ELSE failed_attempts+1 END,locked_until=CASE WHEN (CASE WHEN locked_until IS NOT NULL AND locked_until<=clock_timestamp() THEN 1 ELSE failed_attempts+1 END)>=5 THEN clock_timestamp()+interval '15 minutes' ELSE NULL END WHERE id=?",user.id()); }
    public void success(long id) { jdbc.update("UPDATE app_users SET failed_attempts=0,locked_until=NULL,last_login_at=clock_timestamp() WHERE id=?",id); }
    public void audit(String actor,String action,String subject) { jdbc.update("INSERT INTO audit_log(actor,action,subject) VALUES(?,?,?)",actor,action,subject); }
    public List<Map<String,Object>> list() { return jdbc.queryForList("SELECT id,username,role,enabled,locked_until,last_login_at FROM app_users ORDER BY id LIMIT 100"); }
    public void update(long id,String role,boolean enabled) { if(jdbc.update("UPDATE app_users SET role=?,enabled=? WHERE id=?",role,enabled,id)!=1) throw new NoSuchElementException(); }
    public record User(long id,String username,String passwordHash,String role,boolean enabled,int failures,Instant lockedUntil) { }
}
