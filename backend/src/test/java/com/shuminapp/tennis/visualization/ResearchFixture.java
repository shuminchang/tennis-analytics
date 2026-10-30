package com.shuminapp.tennis.visualization;

import com.fasterxml.jackson.databind.*;
import com.shuminapp.tennis.features.FeatureData;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;

final class ResearchFixture {
    static void seed(JdbcTemplate jdbc,ObjectMapper json) throws Exception {
        FeatureData.clear(jdbc);
        long snapshot=jdbc.queryForObject("INSERT INTO source_snapshots(repository_url,commit_sha,archive_location) VALUES('https://example.com/fixture',?,'/private/archive') RETURNING id",Long.class,"a".repeat(40));
        long players=run(jdbc,snapshot,"PLAYERS");run(jdbc,snapshot,"RANKINGS");long run=run(jdbc,snapshot,"MATCHES");
        long pf=file(jdbc,snapshot,"PLAYERS"),file=file(jdbc,snapshot,"MATCHES");
        Path path=Path.of("docs/fixtures/visualization-v1-hand-example.json");if(!Files.exists(path)) path=Path.of("..",path.toString());
        var fixture=json.readTree(path.toFile());
        for(var p:fixture.get("players")) {
            int id=p.get("id").asInt();jdbc.update("INSERT INTO players(tour,player_id,name_first,height_cm,hand) VALUES('ATP',?,'Fixture',?,?)",id,integer(p,"height"),p.get("hand").isNull()?null:p.get("hand").asText());
            jdbc.update("INSERT INTO player_sources(run_id,snapshot_id,source_file_id,record_number,tour,player_id) VALUES(?,?,?,?,'ATP',?)",players,snapshot,pf,id,id);
            if(p.path("heightBlocked").asBoolean()) {
                long issue=jdbc.queryForObject("INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,role,action,reason) VALUES(?,?,?,?,'ATP','PLAYERS','HEIGHT','PLAYER','FLAG','fixture') RETURNING id",Long.class,players,snapshot,pf,id);
                jdbc.update("INSERT INTO quality_issue_impacts VALUES(?,'HEIGHT_ANALYSIS')",issue);
            }
        }
        jdbc.update("INSERT INTO tournament_decisions(run_id,snapshot_id,tour,tourney_id,source_file_id,record_number,scope_status,analysis_category,rule_code,reason,evidence) VALUES(?,?,'ATP','fixture',?,1,'INCLUDE','SOURCE_A','FIXTURE','fixture','fixture')",run,snapshot,file);
        for(var m:json.readTree(path.toFile()).get("matches")) {
            int number=m.get("id").asInt();var w=m.get("winner");var l=m.get("loser");
            long id=jdbc.queryForObject("""
                INSERT INTO matches(tour,tourney_id,tourney_name,tourney_date,tourney_level,round,match_num,winner_id,loser_id,score,surface,winner_age,loser_age,w_ace,w_df,w_svpt,w_1st_in,w_1st_won,l_ace,l_df,l_svpt,l_1st_in,l_1st_won)
                VALUES('ATP','fixture','Fixture','2024-01-01','A','R32',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,number,w.get("player").asInt(),l.get("player").asInt(),m.get("score").asText(),m.get("surface").asText(),w.get("age").asDouble(),l.get("age").asDouble(),integer(w,"ace"),integer(w,"df"),integer(w,"svpt"),integer(w,"firstIn"),integer(w,"firstWon"),integer(l,"ace"),integer(l,"df"),integer(l,"svpt"),integer(l,"firstIn"),integer(l,"firstWon"));
            jdbc.update("INSERT INTO match_sources(run_id,snapshot_id,source_file_id,record_number,tour,match_id) VALUES(?,?,?,?,'ATP',?)",run,snapshot,file,number,id);
            for(String role:new String[]{"winner","loser"}) {
                var side=m.get(role);for(String metric:new String[]{"AGE_ANALYSIS","SERVE_STATS"}) if(side.path(metric.equals("AGE_ANALYSIS")?"ageBlocked":"serveBlocked").asBoolean()) {
                    long issue=jdbc.queryForObject("INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,role,action,reason) VALUES(?,?,?,?,'ATP','MATCHES',?,?,'FLAG','fixture') RETURNING id",Long.class,run,snapshot,file,number,metric,role.toUpperCase());jdbc.update("INSERT INTO quality_issue_impacts VALUES(?,?)",issue,metric);
                }
            }
        }
        FeatureData.refresh(jdbc);
    }
    private static long run(JdbcTemplate jdbc,long snapshot,String kind) { return jdbc.queryForObject("INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,data_kind,status,finished_at) VALUES(?,?,?,?,?,?,'SUCCEEDED',clock_timestamp()) RETURNING id",Long.class,snapshot,"b".repeat(40),"c".repeat(64),"d".repeat(64),"e".repeat(64),kind); }
    private static long file(JdbcTemplate jdbc,long snapshot,String kind) { return jdbc.queryForObject("INSERT INTO source_files(snapshot_id,relative_path,tour,data_kind,sha256,byte_size,record_count) VALUES(?,?,'ATP',?,?,0,0) RETURNING id",Long.class,snapshot,kind+".csv",kind,"f".repeat(64)); }
    private static Integer integer(JsonNode node,String field) { return node.path(field).isNull()?null:node.path(field).asInt(); }
}
