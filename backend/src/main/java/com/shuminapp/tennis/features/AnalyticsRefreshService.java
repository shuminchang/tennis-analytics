package com.shuminapp.tennis.features;

import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class AnalyticsRefreshService {
    private final JdbcTemplate jdbc;private final CacheManager caches;
    public AnalyticsRefreshService(JdbcTemplate jdbc,CacheManager caches) { this.jdbc=jdbc;this.caches=caches; }
    @Transactional
    public void refresh() {
        jdbc.execute("SELECT pg_advisory_xact_lock(714031)");jdbc.execute("SET LOCAL statement_timeout=0");
        if(jdbc.queryForObject("SELECT count(*) FROM import_runs WHERE status='RUNNING'",Long.class)>0 || jdbc.queryForObject("SELECT count(*) FROM elo_runs WHERE status='RUNNING'",Long.class)>0) throw new IllegalStateException("Inspect running batch before refresh");
        jdbc.execute("REFRESH MATERIALIZED VIEW analytics_matches");jdbc.execute("REFRESH MATERIALIZED VIEW analytics_events");
        jdbc.execute("ANALYZE analytics_matches");jdbc.execute("ANALYZE analytics_events");
        jdbc.update("INSERT INTO analytics_state VALUES(true,clock_timestamp(),(SELECT max(id) FROM import_runs WHERE data_kind='MATCHES' AND status='SUCCEEDED')) ON CONFLICT(singleton) DO UPDATE SET refreshed_at=excluded.refreshed_at,source_run_id=excluded.source_run_id");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { clear(); } });
    }
    public void clear() { for(String name:caches.getCacheNames()) { var cache=caches.getCache(name);if(cache!=null) cache.clear(); } }
}
