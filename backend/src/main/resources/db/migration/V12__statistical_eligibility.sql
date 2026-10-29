CREATE VIEW v_display_matches AS
SELECT m.* FROM matches m WHERE EXISTS (
    SELECT 1 FROM match_sources s JOIN import_runs r ON r.id=s.run_id
    JOIN tournament_decisions d ON d.run_id=r.id AND d.tour=m.tour AND d.tourney_id=m.tourney_id
    WHERE s.match_id=m.id AND s.tour=m.tour AND r.status='SUCCEEDED' AND r.data_kind='MATCHES' AND d.scope_status='INCLUDE'
) AND NOT EXISTS (
    SELECT 1 FROM match_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
    JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.match_id=m.id AND s.tour=m.tour AND q.status='OPEN' AND i.metric_code='MATCH_HISTORY'
);

-- stats-v1: walkovers/defaults/unknown or unparseable scores excluded; played retirements included.
CREATE VIEW v_stat_matches AS
SELECT m.* FROM v_display_matches m
WHERE m.score ~* '^([0-9]+-[0-9]+(\([0-9]+\))?[[:space:]]*)+(RET)?$'
  AND m.score ~ '[1-9]'
  AND NOT EXISTS (
    SELECT 1 FROM match_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
    JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.match_id=m.id AND s.tour=m.tour AND q.status='OPEN' AND i.metric_code IN ('PERSONAL_STATS','SCORE_ANALYSIS')
  ) AND NOT EXISTS (
    SELECT 1 FROM player_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
    JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.tour=m.tour AND s.player_id IN (m.winner_id,m.loser_id) AND q.status='OPEN' AND i.metric_code='PERSONAL_STATS'
  );
