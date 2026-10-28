package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import com.fasterxml.jackson.databind.JsonNode;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfiguration.class)
class PlayerSearchApiTest {
    @Autowired PlayerImportService importer;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @TempDir Path temp;
    @BeforeEach void seed() throws Exception {
        var fixture = new GitFixture(temp.resolve("repo"));
        String header=GitFixture.PLAYERS.substring(0,GitFixture.PLAYERS.indexOf('\n')+1);
        fixture.write("atp/atp_players.csv",header+"1,Roger,Federer,R,19810808,SUI,185,Q1\n2,U,Unknown,U,,,,\n3,Percent,100%,L,,,,\n4,Ann,Under_score,,,,,\n");
        fixture.write("wta/wta_players.csv",header+"1,Serena,Williams,R,19810926,USA,175,Q2\n");
        var identity=fixture.commit();Path archive=temp.resolve("archive");
        new SourceSnapshotStore().prepare(fixture.root,identity,archive);
        importer.importPlayers(fixture.root,identity,archive,"a".repeat(40));
        jdbc.update("INSERT INTO players(tour,player_id,name_first,name_last) VALUES('ATP',999,'Not','Published')");
    }
    @AfterEach void cleanup() throws Exception {
        jdbc.execute("TRUNCATE quality_issue_impacts,quality_issues,player_sources,players,source_files,source_snapshots,import_runs CASCADE");
        try(var paths=Files.walk(temp)) {
            for(var p:paths.sorted().toList()) if(Files.isDirectory(p)) Files.setPosixFilePermissions(p,PosixFilePermissions.fromString("rwx------"));
        }
    }
    JsonNode search(String query) {
        var response=http.getForEntity("/v1/players"+query,JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }
    @Test void searchesCaseInsensitivelyAndReturnsBasicFields() {
        var body=search("?q=fEdErEr&tour=atp");
        assertThat(body.get("total").asLong()).isEqualTo(1);
        assertThat(body.at("/items/0/nameFirst").asText()).isEqualTo("Roger");
        assertThat(body.at("/items/0/dob").asText()).isEqualTo("1981-08-08");
        assertThat(search("?q=Federer&tour=WTA").get("total").asLong()).isZero();
    }
    @Test void excludesUnpublishedAndPlaceholderAndPaginatesStably() {
        var first=search("?size=2");var second=search("?size=2&page=1");
        assertThat(first.get("total").asLong()).isEqualTo(4);
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(first.get("items").get(0)).isNotEqualTo(second.get("items").get(0));
        assertThat(search("?q=Unknown").get("total").asLong()).isZero();
        assertThat(search("?q=Published").get("total").asLong()).isZero();
        assertThat(search("?page=999").get("items").size()).isZero();
        assertThat(search("?tour=WTA").at("/items/0/playerId").asInt()).isEqualTo(1);
    }
    @Test void treatsSqlWildcardsAsLiteralText() {
        var response=http.getForEntity("/v1/players?q={q}",JsonNode.class,"%");
        assertThat(response.getBody().get("total").asLong()).isEqualTo(1);
        assertThat(search("?q=_").get("total").asLong()).isEqualTo(1);
        assertThat(search("?q=' OR 1=1 --").get("total").asLong()).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings={"?page=-1","?size=0","?size=101","?tour=INVALID","?page=oops","?page=10001"})
    void invalidParametersHaveStableErrorContract(String query) {
        var response=http.getForEntity("/v1/players"+query,JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_SEARCH");
        assertThat(response.getBody().has("trace")).isFalse();
    }
    @Test void detailsUseTourAndIdAndReturnBasicFields() {
        var atp=http.getForEntity("/v1/players/atp/1",JsonNode.class);
        var wta=http.getForEntity("/v1/players/WTA/1",JsonNode.class);
        assertThat(atp.getStatusCode().value()).isEqualTo(200);
        assertThat(wta.getStatusCode().value()).isEqualTo(200);
        assertThat(atp.getBody().get("tour").asText()).isEqualTo("ATP");
        assertThat(atp.getBody().get("playerId").asInt()).isEqualTo(1);
        assertThat(atp.getBody().get("nameLast").asText()).isEqualTo("Federer");
        assertThat(atp.getBody().get("dob").asText()).isEqualTo("1981-08-08");
        assertThat(atp.getBody().get("hand").asText()).isEqualTo("R");
        assertThat(atp.getBody().get("ioc").asText()).isEqualTo("SUI");
        assertThat(wta.getBody().get("nameLast").asText()).isEqualTo("Williams");
    }
    @Test void detailsPreserveMissingFieldsAsNull() {
        var response=http.getForEntity("/v1/players/ATP/4",JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        for(String field:new String[]{"dob","hand","ioc"}) {
            assertThat(response.getBody().get(field).isNull()).isTrue();
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"ATP/2","ATP/999","ATP/123456"})
    void detailsHidePlaceholderUnpublishedAndMissingPlayers(String identity) {
        var response=http.getForEntity("/v1/players/"+identity,JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PLAYER_NOT_FOUND");
        assertThat(response.getBody().has("trace")).isFalse();
    }
    @ParameterizedTest
    @ValueSource(strings={"ITF/1","ATP/0","ATP/-1","ATP/oops","ATP/2147483648"})
    void invalidDetailIdentityReturns400(String identity) {
        var response=http.getForEntity("/v1/players/"+identity,JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_PLAYER_IDENTITY");
        assertThat(response.getBody().has("trace")).isFalse();
    }
}
