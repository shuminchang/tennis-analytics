SET LOCAL statement_timeout=0;
CREATE MATERIALIZED VIEW analytics_matches AS
SELECT m.*,s.id IS NOT NULL AS eligible FROM v_display_matches m LEFT JOIN v_stat_matches s ON s.id=m.id;
CREATE UNIQUE INDEX uq_analytics_match ON analytics_matches(id);
CREATE INDEX ix_analytics_winner ON analytics_matches(tour,winner_id,tourney_date);
CREATE INDEX ix_analytics_loser ON analytics_matches(tour,loser_id,tourney_date);
CREATE INDEX ix_analytics_event ON analytics_matches(tour,tourney_id);
CREATE MATERIALIZED VIEW analytics_events AS
SELECT tour,tourney_id,max(tourney_name) AS name,max(tourney_date) AS date,max(surface) AS surface,
    max(tourney_level) AS level,count(*) AS matches,
    CASE WHEN count(*) FILTER(WHERE eligible AND round='F' AND coalesce(tourney_level,'')<>'D')=1
        THEN min(winner_id) FILTER(WHERE eligible AND round='F' AND coalesce(tourney_level,'')<>'D') END AS champion_id
FROM analytics_matches GROUP BY tour,tourney_id;
CREATE UNIQUE INDEX uq_analytics_event ON analytics_events(tour,tourney_id);
CREATE INDEX ix_analytics_event_date ON analytics_events(tour,date DESC);
CREATE TABLE analytics_state (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    refreshed_at timestamptz NOT NULL,
    source_run_id bigint
);
INSERT INTO analytics_state(singleton,refreshed_at,source_run_id) SELECT true,clock_timestamp(),max(id) FROM import_runs WHERE data_kind='MATCHES' AND status='SUCCEEDED';
