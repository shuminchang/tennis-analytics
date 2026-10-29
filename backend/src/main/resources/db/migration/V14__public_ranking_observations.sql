CREATE VIEW v_public_rankings AS
SELECT r.* FROM rankings r WHERE EXISTS (
    SELECT 1 FROM ranking_sources s JOIN import_runs i ON i.id=s.run_id WHERE s.tour=r.tour AND s.player_id=r.player_id AND s.ranking_date=r.ranking_date AND i.status='SUCCEEDED' AND i.data_kind='RANKINGS'
) AND EXISTS (
    SELECT 1 FROM player_sources s JOIN import_runs i ON i.id=s.run_id WHERE s.tour=r.tour AND s.player_id=r.player_id AND i.status='SUCCEEDED'
) AND NOT EXISTS (
    SELECT 1 FROM player_sources s JOIN quality_issues q ON q.run_id=s.run_id AND q.source_file_id=s.source_file_id AND q.record_number=s.record_number
    JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE s.tour=r.tour AND s.player_id=r.player_id AND q.status='OPEN' AND i.metric_code='PLAYER_SEARCH'
);
