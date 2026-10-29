package com.shuminapp.tennis.features;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.shuminapp.tennis.auth.UserRepository;

@RestController
public class OpsController {
    private final JdbcTemplate jdbc;private final AnalyticsRefreshService analytics;private final UserRepository users;
    public OpsController(JdbcTemplate jdbc,AnalyticsRefreshService analytics,UserRepository users) { this.jdbc=jdbc;this.analytics=analytics;this.users=users; }
    @GetMapping("/v1/admin/status") public Map<String,Object> status() { return Map.of("imports",jdbc.queryForList("SELECT id,data_kind,status,started_at,finished_at,error_summary FROM import_runs ORDER BY id DESC LIMIT 20"),"models",jdbc.queryForList("SELECT id,status,matches,started_at,finished_at,error_summary FROM elo_runs ORDER BY id DESC LIMIT 10"),"analytics",jdbc.queryForList("SELECT * FROM analytics_state")); }
    @PostMapping("/v1/admin/cache/clear") public void clear(Authentication auth) { analytics.clear();users.audit(auth.getName(),"CACHE_CLEARED",null); }
    @PostMapping("/v1/admin/analytics/refresh") public void refresh(Authentication auth) { analytics.refresh();users.audit(auth.getName(),"ANALYTICS_REFRESHED",null); }
}
