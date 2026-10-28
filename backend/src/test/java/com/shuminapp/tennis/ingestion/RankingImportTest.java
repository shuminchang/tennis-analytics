package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.JsonNode;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class RankingImportTest {
    @Autowired RankingImportService rankings;
    @Autowired PlayerImportService players;
    @Autowired SourceRegistrationService registration;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @TempDir Path temp;
    GitFixture fixture; SourceSnapshotIdentity identity; Path archive;
    static final String ATP="ranking_date,rank,player,points\n";
    static final String WTA="ranking_date,rank,player,points,tours\n";
    void prepare(String atp,String current,String wta,boolean importPlayers) throws Exception {
        fixture=new GitFixture(temp.resolve("repo"));
        String header="player_id,name_first,name_last,hand,dob,ioc,height,wikidata_id\n";
        fixture.write("atp/atp_players.csv",header+"1,Roger,Federer,R,19810808,SUI,185,Q1\n2,U,Unknown,U,,,,\n");
        fixture.write("wta/wta_players.csv",header+"1,Serena,Williams,R,19810926,USA,175,Q2\n");
        fixture.write("atp/atp_rankings_20s.csv",ATP+atp);
        fixture.write("atp/atp_rankings_current.csv",ATP+current);
        fixture.write("wta/wta_rankings_20s.csv",WTA+wta);
        identity=fixture.commit();archive=temp.resolve("archive");
        new SourceSnapshotStore().prepare(fixture.root,identity,archive);
        if(importPlayers) players.importPlayers(fixture.root,identity,archive,"a".repeat(40));
    }
    @AfterEach void cleanup() throws Exception {
        jdbc.execute("TRUNCATE quality_issue_impacts,quality_issues,ranking_sources,rankings,player_sources,players,source_files,source_snapshots,import_runs CASCADE");
        try(var paths=Files.walk(temp)) {
            for(var path:paths.sorted().toList()) if(Files.isDirectory(path)) Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"));
        }
    }
    @Test void mergesIdenticalSourcesQuarantinesConflictsAndServesHistory() throws Exception {
        prepare("20240101,1,1,100\n20240108,2,1,\n20240115,3,1,0\n20240122,4,1,10\n20240129,4,1,bad\n20240205,4,999,10\nx,2,1,10\n",
                "20240101,1,1,100\n20240122,5,1,10\n20240129,4,1,0\n",
                "20240101,1,1,0,0\n20240108,2,1,10,\n20240108,2,1,10,0\n",true);
        var before=http.getForEntity("/v1/players/ATP/1/rankings",JsonNode.class);
        assertThat(before.getBody().get("available").asBoolean()).isFalse();
        var result=rankings.importRankings(fixture.root,identity,archive,"b".repeat(40));
        assertThat(result.imported()).isEqualTo(4);
        assertThat(result.sourceRecords()).isEqualTo(5);
        assertThat(result.quarantined()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quality_issues WHERE run_id=? AND evidence->>'points'='bad'",Long.class,result.runId())).isEqualTo(1);
        assertThat(rankings.importRankings(fixture.root,identity,archive,"b".repeat(40)).reused()).isTrue();
        assertThat(players.importPlayers(fixture.root,identity,archive,"a".repeat(40)).reused()).isTrue();
        assertThrows(IllegalStateException.class,()->rankings.importRankings(fixture.root,identity,archive,"c".repeat(40)));
        var page=http.getForEntity("/v1/players/atp/1/rankings?size=2",JsonNode.class);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        assertThat(page.getBody().get("available").asBoolean()).isTrue();
        assertThat(page.getBody().get("total").asLong()).isEqualTo(3);
        assertThat(page.getBody().at("/items/0/date").asText()).isEqualTo("2024-01-15");
        assertThat(page.getBody().at("/items/0/points").asInt()).isZero();
        assertThat(page.getBody().at("/items/1/points").isNull()).isTrue();
        assertThat(http.getForEntity("/v1/players/ATP/1/rankings?size=2&page=1",JsonNode.class).getBody().at("/items/0/date").asText()).isEqualTo("2024-01-01");
        assertThat(http.getForEntity("/v1/players/WTA/1/rankings",JsonNode.class).getBody().get("total").asLong()).isEqualTo(1);
        for(String id:new String[]{"2","999"}) assertThat(http.getForEntity("/v1/players/ATP/"+id+"/rankings",JsonNode.class).getStatusCode().value()).isEqualTo(404);
        for(String path:new String[]{"ATP/1/rankings?page=-1","ATP/1/rankings?size=101","ATP/1/rankings?page=x","ATP/0/rankings","ITF/1/rankings"}) {
            var response=http.getForEntity("/v1/players/"+path,JsonNode.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_RANKING_QUERY");
        }
    }
    @Test void requiresSameSnapshotPlayersAndNoConcurrentRun() throws Exception {
        prepare("20240101,1,1,100\n","","20240101,1,1,0,0\n",false);
        assertThrows(IllegalStateException.class,()->rankings.importRankings(fixture.root,identity,archive,"b".repeat(40)));
        players.importPlayers(fixture.root,identity,archive,"a".repeat(40));
        jdbc.update("UPDATE import_runs SET status='RUNNING',finished_at=NULL");
        assertThrows(IllegalStateException.class,()->rankings.importRankings(fixture.root,identity,archive,"b".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rankings",Long.class)).isZero();
    }
    @Test void failureRollsBackStagingAndAllowsRetry() throws Exception {
        prepare("20240101,1,1,100\n","","20240101,1,1,0,0\n",true);
        var registered=registration.register(fixture.root,identity,archive);
        Path path=archive.resolve("wta/wta_rankings_20s.csv");String original=Files.readString(path);
        Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));Files.writeString(path,"tampered");
        assertThrows(RuntimeException.class,()->rankings.importRegistered(registered,archive,"b".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rankings",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking_sources",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM import_runs WHERE data_kind='RANKINGS'",String.class)).isEqualTo("FAILED");
        Files.writeString(path,original);Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("r--r--r--"));
        assertThat(rankings.importRankings(fixture.root,identity,archive,"b".repeat(40)).imported()).isEqualTo(2);
    }
    @Test void flushesMultipleBatchesAndReturnsEmptyForNoObservedHistory() throws Exception {
        StringBuilder rows=new StringBuilder();
        for(int n=0;n<2001;n++) rows.append(LocalDate.of(2000,1,1).plusDays(n).format(DateTimeFormatter.BASIC_ISO_DATE)).append(",1,1,0\n");
        prepare(rows.toString(),"","",true);
        assertThat(rankings.importRankings(fixture.root,identity,archive,"b".repeat(40)).imported()).isEqualTo(2001);
        var empty=http.getForEntity("/v1/players/WTA/1/rankings",JsonNode.class).getBody();
        assertThat(empty.get("available").asBoolean()).isTrue();assertThat(empty.get("total").asLong()).isZero();
    }
}
