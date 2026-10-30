package com.shuminapp.tennis.visualization;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import com.shuminapp.tennis.features.FeatureData;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class VisualizationApiTest {
    @Autowired VisualizationRepository repository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    long snapshot,playersRun,rankingsRun,pfile,rfile;
    static final String BASE="/v1/visualizations";
    @AfterEach void cleanup() { FeatureData.clear(jdbc); }
    @BeforeEach void seed() throws Exception {
        FeatureData.clear(jdbc);
        snapshot=jdbc.queryForObject("INSERT INTO source_snapshots(repository_url,commit_sha,archive_location) VALUES('https://example.com/fixture',?,'/private/archive') RETURNING id",Long.class,"a".repeat(40));
        playersRun=run("PLAYERS",snapshot);rankingsRun=run("RANKINGS",snapshot);
        pfile=file("PLAYERS","ATP");rfile=file("RANKINGS","ATP");
        Path path=Path.of("docs/fixtures/visualization-v1-hand-example.json");if(!Files.exists(path)) path=Path.of("..",path.toString());
        var fixture=json.readTree(path.toFile());
        for(var p:fixture.get("players")) {
            int id=p.get("id").asInt();
            jdbc.update("INSERT INTO players(tour,player_id,name_first,height_cm,hand) VALUES('ATP',?,'Fixture',?,?)",id,p.get("height").isNull()?null:p.get("height").asInt(),p.get("hand").isNull()?null:p.get("hand").asText());
            jdbc.update("INSERT INTO player_sources(run_id,snapshot_id,source_file_id,record_number,tour,player_id) VALUES(?,?,?,?,'ATP',?)",playersRun,snapshot,pfile,id,id);
            if(p.path("heightBlocked").asBoolean()) block(id,"HEIGHT_ANALYSIS");
        }
        for(var r:fixture.get("rankings")) ranking("ATP",r.get("player").asInt(),r.get("rank").asInt(),r.get("points").isNull()?null:r.get("points").asInt(),rfile);
    }
    long run(String kind,long snap) { return jdbc.queryForObject("INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,data_kind,status,finished_at) VALUES(?,?,?,?,?,?,'SUCCEEDED',clock_timestamp()) RETURNING id",Long.class,snap,"b".repeat(40),"c".repeat(64),"d".repeat(64),"e".repeat(64),kind); }
    long file(String kind,String tour) { return jdbc.queryForObject("INSERT INTO source_files(snapshot_id,relative_path,tour,data_kind,sha256,byte_size,record_count) VALUES(?,?,?,?,?,0,0) RETURNING id",Long.class,snapshot,tour+kind,tour,kind,"f".repeat(64)); }
    void ranking(String tour,int id,int rank,Integer points,long file) {
        jdbc.update("INSERT INTO rankings(tour,ranking_date,player_id,rank,points) VALUES(?,'2024-01-01',?,?,?)",tour,id,rank,points);
        jdbc.update("INSERT INTO ranking_sources(run_id,snapshot_id,source_file_id,record_number,tour,ranking_date,player_id) VALUES(?,?,?,?,?,'2024-01-01',?)",rankingsRun,snapshot,file,id,tour,id);
    }
    void block(int id,String metric) {
        long issue=jdbc.queryForObject("INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,role,action,reason) VALUES(?,?,?,?,'ATP','PLAYERS',?,'PLAYER','FLAG','fixture') RETURNING id",Long.class,playersRun,snapshot,pfile,id,metric);
        jdbc.update("INSERT INTO quality_issue_impacts VALUES(?,?)",issue,metric);
    }
    JsonNode getJson(String path) throws Exception { return json.readTree(mvc.perform(get(BASE+path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    @Test void sameHandFixtureAndPublicMetadataHaveCompleteProvenance() throws Exception {
        var meta=getJson("/metadata?tour=ATP");assertThat(meta.path("latestRankingDate").asText()).isEqualTo("2024-01-01");
        assertThat(meta.path("source").path("playerRunId").asLong()).isEqualTo(playersRun);
        assertThat(meta.path("source").path("rankingRules").path("qualitySha").asText()).isEqualTo("d".repeat(64));
        var result=getJson("/scatter?tour=ATP");var sample=result.path("sample");
        assertThat(sample.path("population").asInt()).isEqualTo(4);assertThat(sample.path("valid").asInt()).isEqualTo(1);assertThat(sample.path("excluded").asInt()).isEqualTo(3);
        assertThat(result.path("items").get(0).path("x").asInt()).isEqualTo(180);
        assertThat(result.path("axes").path("y").path("direction").asText()).isEqualTo("descending");
        assertThat(result.toString()).doesNotContain("/private/archive","password_hash");
        var hist=getJson("/histogram?tour=ATP");assertThat(hist.path("bins").size()).isEqualTo(1);assertThat(hist.path("bins").get(0).path("lower").asDouble()).isEqualTo(179.5);
    }
    @Test void exactEmptyDateAndMissingRequiredParameters() throws Exception {
        var empty=getJson("/scatter?tour=ATP&rankingDate=2024-01-02");assertThat(empty.path("items").isEmpty()).isTrue();assertThat(empty.path("context").path("rankingDate").asText()).isEqualTo("2024-01-02");
        var hist=getJson("/histogram?tour=ATP&rankingDate=2024-01-02");assertThat(hist.path("domain").isNull()).isTrue();assertThat(hist.path("bins").isEmpty()).isTrue();
        mvc.perform(get(BASE+"/metadata")).andExpect(status().isBadRequest());
    }
    @Test void nullPointsZeroPointsAndAxisSwapHaveStableExclusions() throws Exception {
        jdbc.update("UPDATE players SET height_cm=170 WHERE tour='ATP' AND player_id=2");
        var normal=getJson("/scatter?tour=ATP&x=height_cm&y=ranking_points");var swapped=getJson("/scatter?tour=ATP&x=ranking_points&y=height_cm");
        assertThat(normal.path("sample")).isEqualTo(swapped.path("sample"));assertThat(normal.path("items").get(0).path("y").asInt()).isZero();
        assertThat(normal.path("sample").path("valid").asInt()).isEqualTo(1);
        assertThat(getJson("/histogram?tour=ATP").path("sample").path("valid").asInt()).isEqualTo(2);
    }
    @Test void hiddenPlayerIsOutsidePopulationQualityCanBeResolved() throws Exception {
        block(2,"PLAYER_SEARCH");assertThat(getJson("/scatter?tour=ATP").path("sample").path("population").asInt()).isEqualTo(3);
        jdbc.update("UPDATE quality_issues SET status='RESOLVED',resolution_note='verified',resolved_at=clock_timestamp() WHERE record_number=4");
        var result=getJson("/scatter?tour=ATP");assertThat(result.path("sample").path("valid").asInt()).isEqualTo(2);
        assertThat(result.path("items").get(1).path("group").asText()).isEqualTo("UNKNOWN");
    }
    @Test void histogramBoundariesEmptyBinsAndHeightDomain() throws Exception {
        jdbc.update("UPDATE quality_issues SET status='DISMISSED',resolution_note='fixture',resolved_at=clock_timestamp()");
        jdbc.update("UPDATE players SET height_cm=CASE player_id WHEN 1 THEN 100 WHEN 2 THEN 175 WHEN 3 THEN 250 ELSE 50 END WHERE tour='ATP'");
        var result=getJson("/histogram?tour=ATP&bins=4");assertThat(result.path("sample").path("valid").asInt()).isEqualTo(3);
        assertThat(result.path("bins").get(0).path("count").asInt()).isEqualTo(1);assertThat(result.path("bins").get(1).path("count").asInt()).isZero();
        assertThat(result.path("bins").get(2).path("count").asInt()).isEqualTo(1);assertThat(result.path("bins").get(3).path("count").asInt()).isEqualTo(1);
        assertThat(result.path("bins").get(3).path("upperInclusive").asBoolean()).isTrue();
    }
    @Test void tiesAreIncludedAndCrossTourIdsRemainSeparate() throws Exception {
        jdbc.update("UPDATE rankings SET rank=1 WHERE tour='ATP'");assertThat(getJson("/scatter?tour=ATP&topN=1").path("sample").path("population").asInt()).isEqualTo(4);
        long pf=file("PLAYERS","WTA"),rf=file("RANKINGS","WTA");jdbc.update("INSERT INTO players(tour,player_id,height_cm) VALUES('WTA',1,190)");
        jdbc.update("INSERT INTO player_sources(run_id,snapshot_id,source_file_id,record_number,tour,player_id) VALUES(?,?,?,1,'WTA',1)",playersRun,snapshot,pf);ranking("WTA",1,1,10,rf);
        assertThat(getJson("/scatter?tour=WTA").path("items").get(0).path("x").asInt()).isEqualTo(190);
    }
    @Test void rejectsUnknownDuplicateInvalidAndInjectionParameters() throws Exception {
        for(String query:new String[]{"tour=ATP&x=elo","tour=ATP&topN=0","tour=ATP&topN=1001","tour=ATP&topN=1&topN=2","tour=ATP&sort=rank","tour=ATP&rankingDate=2024-02-30","tour=ATP&x=height_cm;DROP TABLE players","tour=ATP&color=surface","tour=ATP&rankingDate=0000-01-01","tour=ATP&rankingDate="})
            mvc.perform(get(BASE+"/scatter?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(BASE+"/histogram?tour=ATP&bins=51")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM players",Integer.class)).isEqualTo(4);
    }
    @Test void sourceMissingMixedOrRunningReturnsSanitized503() throws Exception {
        long other=jdbc.queryForObject("INSERT INTO source_snapshots(repository_url,commit_sha,archive_location) VALUES('https://example.com/other',?,'/secret') RETURNING id",Long.class,"f".repeat(40));run("PLAYERS",other);
        mvc.perform(get(BASE+"/scatter?tour=ATP")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        jdbc.update("DELETE FROM import_runs WHERE snapshot_id=?",other);jdbc.update("UPDATE import_runs SET status='RUNNING',finished_at=NULL WHERE id=?",playersRun);
        mvc.perform(get(BASE+"/metadata?tour=ATP")).andExpect(status().isServiceUnavailable());
    }
    @Test void neverSilentlyTruncatesMoreThan2000TiedPlayers() throws Exception {
        jdbc.update("INSERT INTO players(tour,player_id,height_cm) SELECT 'ATP',g,180 FROM generate_series(5,2002) g");
        jdbc.update("INSERT INTO player_sources(run_id,snapshot_id,source_file_id,record_number,tour,player_id) SELECT ?,?,?,g,'ATP',g FROM generate_series(5,2002) g",playersRun,snapshot,pfile);
        jdbc.update("INSERT INTO rankings(tour,ranking_date,player_id,rank) SELECT 'ATP','2024-01-01',g,1 FROM generate_series(5,2002) g");
        jdbc.update("INSERT INTO ranking_sources(run_id,snapshot_id,source_file_id,record_number,tour,ranking_date,player_id) SELECT ?,?,?,g,'ATP','2024-01-01',g FROM generate_series(5,2002) g",rankingsRun,snapshot,rfile);
        mvc.perform(get(BASE+"/scatter?tour=ATP")).andExpect(status().isBadRequest());
    }
    @Test void ignoresUnpublishedNewerRankingsAndEmptyTourHasNullDate() throws Exception {
        jdbc.update("INSERT INTO rankings(tour,ranking_date,player_id,rank) VALUES('ATP','2025-01-01',1,1)");
        assertThat(getJson("/metadata?tour=ATP").path("latestRankingDate").asText()).isEqualTo("2024-01-01");
        assertThat(getJson("/scatter?tour=WTA").path("context").path("rankingDate").isNull()).isTrue();
        assertThat(getJson("/scatter?tour=WTA").path("items").isEmpty()).isTrue();
    }
    @Test void missingSourceAndIncompatibleRulesAreNotPublished() throws Exception {
        run("PLAYERS",snapshot);jdbc.update("UPDATE import_runs SET quality_rules_sha256=? WHERE id=(SELECT max(id) FROM import_runs)","f".repeat(64));
        mvc.perform(get(BASE+"/metadata?tour=ATP")).andExpect(status().isServiceUnavailable());
        FeatureData.clear(jdbc);mvc.perform(get(BASE+"/scatter?tour=ATP")).andExpect(status().isServiceUnavailable());
    }
    @Test void blockedSqlIsCancelledAndReturns503WithoutDetails() throws Exception {
        try(var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement()) { statement.execute("LOCK TABLE rankings IN ACCESS EXCLUSIVE MODE"); }
            long start=System.nanoTime();
            String body=mvc.perform(get(BASE+"/metadata?tour=ATP")).andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
            assertThat((System.nanoTime()-start)/1_000_000).isBetween(4500L,9000L);
            assertThat(body).doesNotContain("SELECT","rankings","PSQLException","/private");
            connection.rollback();
        }
        mvc.perform(get(BASE+"/metadata?tour=ATP")).andExpect(status().isOk());
    }

    @Test void realQualityStatusChangesInvalidateActualCaffeineCache() {
        var service=new VisualizationService(repository,new com.shuminapp.tennis.features.CacheConfiguration().cacheManager());
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var request=new VisualizationRequest("ATP",java.time.LocalDate.of(2024,1,1),100,"height_cm","rank","hand",20);
        var before=tx.execute(status->service.scatter(request));assertThat(before.sample().valid()).isEqualTo(1);
        var cached=tx.execute(status->service.scatter(request));assertThat(cached).isSameAs(before);
        block(1,"HEIGHT_ANALYSIS");var after=tx.execute(status->service.scatter(request));
        assertThat(after.sample().valid()).isZero();assertThat(after.context().source().qualityRevision()).isNotEqualTo(before.context().source().qualityRevision());
    }

}
