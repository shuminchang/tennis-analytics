package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.assertThat;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class RankingBrowseTest {
    @Autowired JdbcTemplate jdbc;@Autowired RankingBrowseService service;
    @Test void keepsTheExactDateAndMissingPoints() {
        FeatureData.seed(jdbc);long snapshot=jdbc.queryForObject("SELECT id FROM source_snapshots",Long.class);
        long run=jdbc.queryForObject("INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,status,finished_at,data_kind) VALUES(?,?,?,?,?,'SUCCEEDED',clock_timestamp(),'RANKINGS') RETURNING id",Long.class,snapshot,"a".repeat(40),"a".repeat(64),"a".repeat(64),"a".repeat(64));
        long file=jdbc.queryForObject("INSERT INTO source_files(snapshot_id,relative_path,tour,data_kind,sha256,byte_size,record_count) VALUES(?,'atp/r.csv','ATP','RANKINGS',?,0,0) RETURNING id",Long.class,snapshot,"a".repeat(64));
        jdbc.update("INSERT INTO rankings VALUES('ATP','2024-01-01',1,1,NULL,NULL)");
        jdbc.update("INSERT INTO ranking_sources(run_id,snapshot_id,source_file_id,record_number,tour,ranking_date,player_id) VALUES(?,?,?,1,'ATP','2024-01-01',1)",run,snapshot,file);
        assertThat(service.board("ATP",null,100).date().toString()).isEqualTo("2024-01-01");
        assertThat(service.board("ATP","2024-01-02",100).items()).isEmpty();
        assertThat(service.chart("ATP",1).items().getFirst().points()).isNull();
    }
}
