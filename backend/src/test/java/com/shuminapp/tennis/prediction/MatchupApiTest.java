package com.shuminapp.tennis.prediction;
import com.fasterxml.jackson.databind.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import com.shuminapp.tennis.auth.AccountService;
import com.shuminapp.tennis.features.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabaseConfiguration.class)
class MatchupApiTest {
    @Autowired JdbcTemplate jdbc;@Autowired EloBuildService elo;@Autowired AccountService accounts;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @MockitoBean MatchupModel models;
    static final String URL="/v1/matchup-prediction?tour=ATP&first=1&second=2&surface=Hard&bestOf=3&asOf=2024-02-20";
    @BeforeEach void seed() throws Exception {
        FeatureData.seed(jdbc);long snap=jdbc.queryForObject("SELECT snapshot_id FROM import_runs LIMIT 1",Long.class);
        for(String kind:new String[]{"PLAYERS","RANKINGS"}) {
            long run=jdbc.queryForObject("INSERT INTO import_runs(snapshot_id,importer_commit_sha,transform_rules_sha256,quality_rules_sha256,scope_rules_sha256,data_kind,status,finished_at) VALUES(?,?,?,?,?,?,'SUCCEEDED',clock_timestamp()) RETURNING id",Long.class,snap,"b".repeat(40),"c".repeat(64),"c".repeat(64),"c".repeat(64),kind);
            if(kind.equals("PLAYERS"))jdbc.update("UPDATE player_sources SET run_id=?",run);
        }
        long run=jdbc.queryForObject("SELECT id FROM import_runs WHERE data_kind='MATCHES'",Long.class),file=jdbc.queryForObject("SELECT id FROM source_files WHERE tour='ATP' AND data_kind='MATCHES'",Long.class);
        jdbc.update("UPDATE matches SET winner_age=20,loser_age=30,winner_height_cm=180,loser_height_cm=170,best_of=3");
        for(int i=0;i<11;i++) {
            long match=jdbc.queryForObject("INSERT INTO matches(tour,tourney_id,tourney_date,round,match_num,winner_id,loser_id,score,surface,best_of,winner_age,loser_age,winner_height_cm,loser_height_cm) VALUES('ATP','event',?,'R32',?,1,2,'6-3 6-4','Hard',3,20,30,180,170) RETURNING id",Long.class,i==10?java.time.LocalDate.of(2024,2,1):java.time.LocalDate.of(2024,1,1),100+i);
            jdbc.update("INSERT INTO match_sources(run_id,snapshot_id,source_file_id,record_number,tour,match_id) VALUES(?,?,?,?,'ATP',?)",run,snap,file,100+i,match);
        }
        elo.rebuild();accounts.create("TEST","predictadvanced","fixture-password-123","ADVANCED");accounts.create("TEST","predictmember","fixture-password-123","MEMBER");
        when(models.compatible(any(),any())).thenReturn(true);
        when(models.fold(anyString(),any())).thenReturn(json.readTree("""
            {"year":2024,"trainThrough":"2021-12-10","validationThrough":"2023-12-10","fallback":"STANDARD_ELO","selected":"ELO_AGE_HEIGHT","eloCoefficients":[2.302585092994046,0,0,0,0,0],"augmentedCoefficients":[2.302585092994046,0,0.1,-0.1,0.2,0],"validation":{"baseline":{"n":200},"elo":{"n":200},"selected":{"n":200},"augmentedCompleteCases":{"n":150}}}
            """));
    }
    @AfterEach void clear(){FeatureData.clear(jdbc);}
    MockHttpSession login(String name)throws Exception{return (MockHttpSession)mvc.perform(post("/auth/login").with(csrf()).param("username",name).param("password","fixture-password-123")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
    JsonNode getData(String url)throws Exception{return json.readTree(mvc.perform(get(url).session(login("predictadvanced"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void cutoffEmbargoExcludesFutureMatchesAndUsesObservedPhysicalData()throws Exception {
        var data=getData(URL);assertThat(data.path("available").asBoolean()).isTrue();assertThat(data.at("/first/overall/matches").asInt()).isEqualTo(12);
        assertThat(data.at("/first/height").asDouble()).isEqualTo(180);assertThat(data.at("/historyBefore").asText()).isEqualTo("2024-01-30");
        assertThat(data.at("/prediction/method").asText()).isEqualTo("ELO_AGE_HEIGHT");assertThat(data.at("/prediction/firstProbability").asDouble()+data.at("/prediction/secondProbability").asDouble()).isCloseTo(1,within(1e-12));
        var reversed=getData(URL.replace("first=1&second=2","first=2&second=1"));assertThat(reversed.at("/prediction/firstProbability").asDouble()).isCloseTo(data.at("/prediction/secondProbability").asDouble(),within(1e-12));
        assertThat(data.toString()).doesNotContain("/fixture","password");
    }
    @Test void insufficientHistoryAndInactivePlayersDoNotGetInventedFiftyPercent()throws Exception {
        var early=getData(URL.replace("2024-02-20","2024-01-22"));assertThat(early.path("available").asBoolean()).isFalse();assertThat(early.path("prediction").isNull()).isTrue();
        var stale=getData(URL.replace("2024-02-20","2025-02-01"));assertThat(stale.path("available").asBoolean()).isFalse();
    }
    @Test void missingPhysicalValuesFallBackToEloWithoutInventingMeasurements()throws Exception {
        jdbc.update("UPDATE matches SET winner_age=NULL,loser_age=NULL,winner_height_cm=NULL,loser_height_cm=NULL");
        var data=getData(URL);assertThat(data.at("/first/age").isNull()).isTrue();assertThat(data.at("/prediction/method").asText()).isEqualTo("STANDARD_ELO");
        assertThat(data.path("warnings").toString()).contains("AGE_OR_HEIGHT_MISSING");
    }
    @Test void rolesAndSourceMismatchAreEnforcedBeforePredictions()throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());mvc.perform(get(URL).session(login("predictmember"))).andExpect(status().isForbidden());
        when(models.compatible(any(),any())).thenReturn(false);mvc.perform(get(URL).session(login("predictadvanced"))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"));
    }
    @Test void requestWhitelistAndFormatsAreBounded()throws Exception {
        var session=login("predictadvanced");
        for(String url:new String[]{URL+"&first=3",URL+"&sql=x",URL.replace("first=1&second=2","first=1&second=1"),URL.replace("bestOf=3","bestOf=7"),URL.replace("asOf=2024-02-20","asOf=2099-01-01"),URL.replace("tour=ATP","tour=WTA").replace("bestOf=3","bestOf=5")})mvc.perform(get(url).session(session)).andExpect(status().isBadRequest());
    }
}
