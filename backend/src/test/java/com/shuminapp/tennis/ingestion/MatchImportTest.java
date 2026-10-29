package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.JsonNode;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class MatchImportTest {
    @Autowired MatchImportService matches;
    @Autowired PlayerImportService players;
    @Autowired SourceRegistrationService registration;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @TempDir Path temp;
    GitFixture fixture;SourceSnapshotIdentity identity;Path archive;
    String row(String id,String level,int number,int winner,int loser,String score) {
        Map<String,String> values=new HashMap<>(Map.of("tourney_id",id,"tourney_name",id,"tourney_level",level,"round","R32","match_num",String.valueOf(number),"tourney_date","20240101","winner_id",String.valueOf(winner),"loser_id",String.valueOf(loser),"score",score));
        values.put("winner_seed","1F");values.put("l_bpSaved","-4");
        return String.join(",",SourceFileType.ATP_MATCHES.expectedHeader().stream().map(h->values.getOrDefault(h,"")).toList())+"\n";
    }
    void prepare(String first,String second,String wta,boolean importPlayers) throws Exception {
        fixture=new GitFixture(temp.resolve("repo"));String header="player_id,name_first,name_last,hand,dob,ioc,height,wikidata_id\n";
        fixture.write("atp/atp_players.csv",header+"1,Roger,Federer,R,19810808,SUI,185,Q1\n2,Rafael,Nadal,L,19860603,ESP,185,Q2\n3,U,Unknown,U,,,,\n4,Future,Player,R,20400101,USA,,\n5,Ann,Unknown Portland,U,,,,\n");
        fixture.write("wta/wta_players.csv",header+"1,Serena,Williams,R,19810926,USA,175,Q3\n2,Venus,Williams,R,19800617,USA,185,Q4\n");
        String matchHeader=String.join(",",SourceFileType.ATP_MATCHES.expectedHeader())+"\n";
        fixture.write("atp/atp_matches_2024.csv",matchHeader+first);fixture.write("atp/atp_matches_2025.csv",matchHeader+second);
        fixture.write("wta/wta_matches_2024.csv",matchHeader+wta);identity=fixture.commit();archive=temp.resolve("archive");new SourceSnapshotStore().prepare(fixture.root,identity,archive);
        if(importPlayers) players.importPlayers(fixture.root,identity,archive,"a".repeat(40));
    }
    @AfterEach void cleanup() throws Exception {
        jdbc.execute("TRUNCATE quality_issue_impacts,quality_issues,match_sources,matches,tournament_decisions,player_sources,players,source_files,source_snapshots,import_runs CASCADE");
        try(var paths=Files.walk(temp)) { for(var path:paths.sorted().toList()) if(Files.isDirectory(path)) Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------")); }
    }
    @Test void importsAllScopeStatesAndOnlyShowsEligibleHistory() throws Exception {
        String first=row("test","A",1,1,2,"6-3 6-4")+row("test","A",2,2,1,"6-2 RET")+row("test","A",3,1,2,"W/O")
                +row("test","A",4,1,3,"6-1 6-1")+row("test","A",5,1,4,"6-1 6-1")+row("test","A",6,1,1,"6-1 6-1")
                +row("test","A",7,1,5,"6-1 6-1")+row("test","A",8,1,999,"6-1 6-1")+row("test","A",9,1,2,"6-1 6-1")
                +row("2017-7696","F",1,1,2,"4-1 4-1")+row("unconfirmed","F",1,1,2,"6-1 6-1")+row("2024-0096","O",1,1,2,"6-1 6-1");
        prepare(first,row("test","A",1,1,2,"6-3 6-4")+row("test","A",9,1,2,"6-2 6-2"),row("wta","G",1,1,2,"6-1 6-1"),true);
        assertThat(http.getForEntity("/v1/players/ATP/1/matches",JsonNode.class).getBody().get("available").asBoolean()).isFalse();
        var result=matches.importMatches(fixture.root,identity,archive,"b".repeat(40));
        assertThat(result.imported()).isEqualTo(11);assertThat(result.sourceRecords()).isEqualTo(12);assertThat(result.quarantined()).isEqualTo(3);
        assertThat(matches.importMatches(fixture.root,identity,archive,"b".repeat(40)).reused()).isTrue();
        assertThat(players.importPlayers(fixture.root,identity,archive,"a".repeat(40)).reused()).isTrue();
        assertThrows(IllegalStateException.class,()->matches.importMatches(fixture.root,identity,archive,"c".repeat(40)));
        var response=http.getForEntity("/v1/players/atp/1/matches?size=20&year=2024",JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);var body=response.getBody();
        assertThat(body.get("total").asInt()).isEqualTo(5);assertThat(body.get("available").asBoolean()).isTrue();
        List<String> statuses=new ArrayList<>(),results=new ArrayList<>();
        body.get("items").forEach(item->{statuses.add(item.get("status").asText());results.add(item.get("result").asText());});
        assertThat(statuses).contains("WALKOVER","RETIRED");assertThat(results).contains("ADVANCED","LOSS");
        assertThat(body.at("/items/0/opponent/nameLast").asText()).isEqualTo("Nadal");
        assertThat(http.getForEntity("/v1/players/WTA/1/matches",JsonNode.class).getBody().get("total").asInt()).isEqualTo(1);
        assertThat(http.getForEntity("/v1/players/ATP/1/matches?year=2023",JsonNode.class).getBody().get("total").asInt()).isZero();
        assertThat(http.getForEntity("/v1/players/ATP/1/matches?size=2&page=1",JsonNode.class).getBody().get("items").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT winner_seed FROM matches WHERE tour='ATP' AND tourney_id='test' AND match_num=1",String.class)).isEqualTo("1F");
        assertThat(jdbc.queryForObject("SELECT l_bp_saved FROM matches WHERE tour='ATP' AND tourney_id='test' AND match_num=1",Integer.class)).isEqualTo(-4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tournament_decisions WHERE scope_status='REVIEW'",Long.class)).isEqualTo(1);
    }
    @Test void rejectsBadQueryAndHidesUnpublishedPlayers() throws Exception {
        prepare(row("test","A",1,1,2,"6-1 6-1"),"",row("wta","G",1,1,2,"UNK"),true);
        matches.importMatches(fixture.root,identity,archive,"b".repeat(40));
        for(String path:List.of("ATP/1/matches?year=0","ATP/1/matches?year=9999","ATP/1/matches?year=x","ATP/1/matches?size=101","ATP/1/matches?page=-1","ITF/1/matches","ATP/0/matches")) {
            var response=http.getForEntity("/v1/players/"+path,JsonNode.class);assertThat(response.getStatusCode().value()).isEqualTo(400);assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_MATCH_QUERY");
        }
        for(int id:List.of(3,999)) assertThat(http.getForEntity("/v1/players/ATP/"+id+"/matches",JsonNode.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/v1/players/WTA/1/matches",JsonNode.class).getBody().at("/items/0/result").asText()).isEqualTo("SOURCE_WINNER");
    }
    @Test void rollsBackWhenTournamentMetadataConflictsAndRetainsFailedRun() throws Exception {
        prepare(row("test","A",1,1,2,"6-1 6-1"),row("test","G",2,1,2,"6-1 6-1"),row("wta","G",1,1,2,"6-1 6-1"),true);
        assertThrows(IllegalStateException.class,()->matches.importMatches(fixture.root,identity,archive,"b".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM tournament_decisions",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM import_runs WHERE data_kind='MATCHES'",String.class)).isEqualTo("FAILED");
    }
    @Test void requiresPlayersAndRejectsChangedArchiveBeforeRegistration() throws Exception {
        prepare(row("test","A",1,1,2,"6-1 6-1"),"",row("wta","G",1,1,2,"6-1 6-1"),false);
        assertThrows(IllegalStateException.class,()->matches.importMatches(fixture.root,identity,archive,"b".repeat(40)));
        Path path=archive.resolve("wta/wta_matches_2024.csv");Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));Files.writeString(path,"tampered");
        assertThrows(java.io.IOException.class,()->matches.importMatches(fixture.root,identity,archive,"b".repeat(40)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_runs",Long.class)).isZero();
    }
}
