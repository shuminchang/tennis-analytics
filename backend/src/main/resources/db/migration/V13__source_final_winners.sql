CREATE VIEW v_event_final_winners AS
SELECT tour,tourney_id,min(winner_id) AS winner_id FROM v_stat_matches
WHERE round='F' AND coalesce(tourney_level,'')<>'D'
GROUP BY tour,tourney_id HAVING count(*)=1;
