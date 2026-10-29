package com.shuminapp.tennis.features;

import org.springframework.jdbc.core.JdbcTemplate;

public final class FeatureData {
    public static void clear(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE elo_history,elo_current,elo_runs,app_users,audit_log,quality_issue_impacts,quality_issues,match_sources,matches,tournament_decisions,ranking_sources,rankings,player_sources,players,source_files,source_snapshots,import_runs CASCADE");
    }
    public static void seed(JdbcTemplate jdbc) {
        clear(jdbc);
        jdbc.execute("TRUNCATE quality_issue_impacts,quality_issues,match_sources,matches,tournament_decisions,player_sources,players,source_files,source_snapshots,import_runs CASCADE");
        long snapshot=jdbc.queryForObject("INSERT INTO source_snapshots(repository_url,commit_sha,archive_location) VALUES('https://example.com/fixture',?,'/fixture') RETURNING id",Long.class,"a".repeat(40));
        long run=jdbc.queryForObject("INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,status,finished_at,data_kind) VALUES(?,?,?,?,?,'SUCCEEDED',clock_timestamp(),'MATCHES') RETURNING id",Long.class,snapshot,"b".repeat(40),"c".repeat(64),"c".repeat(64),"c".repeat(64));
        for(String tour:new String[]{"ATP","WTA"}) {
            long pf=file(jdbc,snapshot,tour,"PLAYERS"),mf=file(jdbc,snapshot,tour,"MATCHES");
            for(int id=1;id<=3;id++) {
                jdbc.update("INSERT INTO players(tour,player_id,name_first,name_last) VALUES(?,?,?,?)",tour,id,id==1?"Alpha":id==2?"Beta":"Gamma","Player");
                jdbc.update("INSERT INTO player_sources(run_id,snapshot_id,source_file_id,record_number,tour,player_id) VALUES(?,?,?,?,?,?)",run,snapshot,pf,id,tour,id);
            }
            jdbc.update("INSERT INTO tournament_decisions(run_id,snapshot_id,tour,tourney_id,source_file_id,record_number,scope_status,analysis_category,rule_code,reason,evidence) VALUES(?,?,?,'event',?,1,'INCLUDE','SOURCE_G','TEST','fixture','fixture')",run,snapshot,tour,mf);
            String[] scores=tour.equals("ATP")?new String[]{"6-3 6-4","6-2 RET","W/O","DEF","UNK","7-Oct"}:new String[]{"6-1 6-1"};
            for(int n=0;n<scores.length;n++) {
                int winner=n==1?2:1,loser=n==1?1:2;
                long match=jdbc.queryForObject("INSERT INTO matches(tour,tourney_id,tourney_name,tourney_level,tourney_date,round,match_num,winner_id,loser_id,score,surface,w_ace,w_df,w_svpt,w_1st_in,w_1st_won,w_2nd_won,l_svpt,l_1st_in,l_1st_won,l_2nd_won) VALUES(?,'event','Fixture Open','G','2024-01-01',?,?,?,?,?,'Hard',5,2,60,40,30,12,50,30,20,10) RETURNING id",Long.class,tour,n==0?"F":n==1?"SF":"R32",n+1,winner,loser,scores[n]);
                jdbc.update("INSERT INTO match_sources(run_id,snapshot_id,source_file_id,record_number,tour,match_id) VALUES(?,?,?,?,?,?)",run,snapshot,mf,n+1,tour,match);
            }
        }
    }
    private static long file(JdbcTemplate jdbc,long snapshot,String tour,String kind) {
        return jdbc.queryForObject("INSERT INTO source_files(snapshot_id,relative_path,tour,data_kind,sha256,byte_size,record_count) VALUES(?,?,?,?,?,0,0) RETURNING id",Long.class,snapshot,tour.toLowerCase()+"/"+kind+".csv",tour,kind,"d".repeat(64));
    }
}
