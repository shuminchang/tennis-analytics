-- Phase 11.1 new metric eligibility checks; no DDL, import or permanent writes.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '90s';
SET LOCAL jit = off;
WITH player_blocks AS MATERIALIZED (
    SELECT s.tour,s.player_id,
        bool_or(i.metric_code='PLAYER_SEARCH') AS hidden,
        bool_or(i.metric_code='HEIGHT_ANALYSIS') AS height_blocked
    FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id
    JOIN player_sources s ON s.run_id=q.run_id AND s.source_file_id=q.source_file_id AND s.record_number=q.record_number
    JOIN import_runs r ON r.id=s.run_id AND r.status='SUCCEEDED'
    WHERE q.status='OPEN' GROUP BY s.tour,s.player_id
), match_blocks AS MATERIALIZED (
    SELECT s.tour,s.match_id,q.role,
        bool_or(i.metric_code='SERVE_STATS') AS serve_blocked,
        bool_or(i.metric_code='AGE_ANALYSIS') AS age_blocked
    FROM quality_issues q JOIN quality_issue_impacts i ON i.issue_id=q.id
    JOIN match_sources s ON s.run_id=q.run_id AND s.source_file_id=q.source_file_id AND s.record_number=q.record_number
    JOIN import_runs r ON r.id=s.run_id AND r.status='SUCCEEDED'
    WHERE q.status='OPEN' GROUP BY s.tour,s.match_id,q.role
), people AS MATERIALIZED (
    SELECT p.*,coalesce(b.height_blocked,false) AS height_blocked,
        p.height_cm BETWEEN 100 AND 250 AND NOT coalesce(b.height_blocked,false) AS height_valid
    FROM players p LEFT JOIN player_blocks b USING(tour,player_id)
    WHERE NOT coalesce(b.hidden,false) AND EXISTS (
        SELECT 1 FROM player_sources s JOIN import_runs r ON r.id=s.run_id
        WHERE s.tour=p.tour AND s.player_id=p.player_id AND r.status='SUCCEEDED'
    )
), perspectives AS MATERIALIZED (
    SELECT m.tour,m.id,v.*,
        NOT coalesce(b.serve_blocked,false) AND NOT coalesce(br.serve_blocked,false) AS serve_quality,
        NOT coalesce(b.age_blocked,false) AND NOT coalesce(br.age_blocked,false) AS age_quality
    FROM analytics_matches m
    CROSS JOIN LATERAL (VALUES
        ('WINNER',m.winner_id,true,m.winner_age,m.w_ace,m.w_df,m.w_svpt,m.w_1st_in,m.w_1st_won),
        ('LOSER',m.loser_id,false,m.loser_age,m.l_ace,m.l_df,m.l_svpt,m.l_1st_in,m.l_1st_won)
    ) v(role,player_id,won,age,ace,df,svpt,first_in,first_won)
    LEFT JOIN match_blocks b ON b.tour=m.tour AND b.match_id=m.id AND b.role=v.role
    LEFT JOIN match_blocks br ON br.tour=m.tour AND br.match_id=m.id AND br.role='RECORD'
    WHERE m.eligible
), usable AS MATERIALIZED (
    SELECT *,serve_quality AND svpt>0 AND ace BETWEEN 0 AND svpt AS ace_valid,
        serve_quality AND svpt>0 AND df BETWEEN 0 AND svpt AS df_valid,
        serve_quality AND first_in>0 AND first_won BETWEEN 0 AND first_in AND (svpt IS NULL OR first_in<=svpt) AS first_valid,
        age_quality AND age>0 AS age_valid
    FROM perspectives
), latest AS (
    SELECT tour,max(ranking_date) AS date FROM rankings GROUP BY tour
), cohort AS MATERIALIZED (
    SELECT r.*,p.height_cm,p.height_valid,p.height_blocked
    FROM latest l JOIN v_public_rankings r ON r.tour=l.tour AND r.ranking_date=l.date
    JOIN people p ON p.tour=r.tour AND p.player_id=r.player_id
    WHERE r.rank<=100
), base AS (
    SELECT t.tour,
        (SELECT count(*) FROM players p WHERE p.tour=t.tour) AS players,
        (SELECT count(*) FROM rankings r WHERE r.tour=t.tour) AS rankings,
        (SELECT count(*) FROM matches m WHERE m.tour=t.tour) AS matches,
        (SELECT count(*) FROM analytics_matches m WHERE m.tour=t.tour) AS displayed,
        (SELECT count(*) FROM analytics_matches m WHERE m.tour=t.tour AND m.eligible) AS eligible
    FROM (VALUES ('ATP'),('WTA')) t(tour)
)
SELECT jsonb_build_object(
    'checked_at',clock_timestamp(),'database',current_database(),
    'transaction_read_only',current_setting('transaction_read_only'),
    'metric_version','visualization-v1',
    'snapshots',(SELECT jsonb_agg(to_jsonb(s)) FROM (SELECT id,repository_url,commit_sha FROM source_snapshots ORDER BY id) s),
    'successful_imports',(SELECT jsonb_agg(to_jsonb(s)) FROM (SELECT id,snapshot_id,data_kind,status FROM import_runs WHERE status='SUCCEEDED' ORDER BY id) s),
    'baseline',(SELECT jsonb_agg(to_jsonb(b) ORDER BY tour) FROM base b),
    'visible_players',(SELECT jsonb_agg(to_jsonb(s) ORDER BY tour) FROM (
        SELECT tour,count(*) AS population,count(*) FILTER(WHERE height_valid) AS height_valid,
            count(*) FILTER(WHERE height_cm IS NULL AND NOT height_blocked) AS height_missing,
            count(*) FILTER(WHERE height_blocked OR (height_cm IS NOT NULL AND NOT height_valid)) AS height_excluded,
            count(*) FILTER(WHERE height_blocked) AS height_quality_blocked
        FROM people GROUP BY tour) s),
    'latest_top100',(SELECT jsonb_agg(to_jsonb(s) ORDER BY tour) FROM (
        SELECT tour,ranking_date,count(*) AS population,count(*) FILTER(WHERE height_valid) AS height_valid,
            count(*) FILTER(WHERE height_cm IS NULL AND NOT height_blocked) AS height_missing,
            count(*) FILTER(WHERE height_blocked OR (height_cm IS NOT NULL AND NOT height_valid)) AS height_excluded,
            count(*) FILTER(WHERE points IS NULL) AS points_missing
        FROM cohort GROUP BY tour,ranking_date) s),
    'perspectives',(SELECT jsonb_agg(to_jsonb(s) ORDER BY tour) FROM (
        SELECT tour,count(*) AS population,count(*) FILTER(WHERE won) AS wins,
            count(*) FILTER(WHERE ace_valid) AS ace_valid,count(*) FILTER(WHERE df_valid) AS df_valid,
            count(*) FILTER(WHERE first_valid) AS first_valid,count(*) FILTER(WHERE age_valid) AS age_valid,
            count(*) FILTER(WHERE NOT serve_quality) AS serve_quality_blocked,
            count(*) FILTER(WHERE NOT age_quality) AS age_quality_blocked,
            min(age) FILTER(WHERE age_valid) AS minimum_valid_age,max(age) FILTER(WHERE age_valid) AS maximum_valid_age,
            sum(ace) FILTER(WHERE ace_valid) AS ace_numerator,sum(svpt) FILTER(WHERE ace_valid) AS ace_denominator
        FROM usable GROUP BY tour) s),
    'elo',(SELECT jsonb_agg(to_jsonb(s)) FROM (SELECT id,status,matches,rules_sha256,input_sha256 FROM elo_runs WHERE status='SUCCEEDED' ORDER BY id) s),
    'analytics_state',(SELECT to_jsonb(s) FROM analytics_state s)
);
ROLLBACK;
