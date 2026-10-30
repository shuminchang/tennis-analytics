-- Read-only production-scale query-plan evidence for Phase 11.2.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='5s';
SET LOCAL jit=off;
EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON)
SELECT p.player_id,concat_ws(' ',p.name_first,p.name_last),p.height_cm,p.hand,r.rank,r.points,
    EXISTS(SELECT 1 FROM player_sources ps JOIN quality_issues q ON q.run_id=ps.run_id AND q.source_file_id=ps.source_file_id AND q.record_number=ps.record_number
        JOIN quality_issue_impacts i ON i.issue_id=q.id WHERE ps.tour=p.tour AND ps.player_id=p.player_id AND ps.run_id=1 AND q.status='OPEN' AND i.metric_code='HEIGHT_ANALYSIS') AS blocked
FROM v_public_rankings r JOIN players p ON p.tour=r.tour AND p.player_id=r.player_id
WHERE r.tour='ATP' AND EXISTS(SELECT 1 FROM ranking_sources s WHERE s.tour=r.tour AND s.player_id=r.player_id AND s.ranking_date=r.ranking_date AND s.run_id=3)
AND EXISTS(SELECT 1 FROM player_sources s WHERE s.tour=p.tour AND s.player_id=p.player_id AND s.run_id=1)
AND r.ranking_date='2026-06-08' AND r.rank<=100 ORDER BY r.rank,r.player_id LIMIT 2001;
ROLLBACK;
