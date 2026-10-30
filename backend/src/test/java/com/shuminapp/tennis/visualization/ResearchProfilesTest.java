package com.shuminapp.tennis.visualization;

import com.fasterxml.jackson.databind.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import com.shuminapp.tennis.features.FeatureData;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabaseConfiguration.class)
class ResearchProfilesTest {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    static final String URL="/v1/visualizations/profile-performance?tour=ATP&fromYear=2024&toYear=2024";
    @BeforeEach void seed() throws Exception { ResearchFixture.seed(jdbc,json); }
    @AfterEach void clear() { FeatureData.clear(jdbc); }
    JsonNode data(String suffix) throws Exception { return json.readTree(mvc.perform(get(URL+suffix)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    @Test void profileTotalsMatchHandWeightedRatesAndIndependentSamples() throws Exception {
        var body=data("");long ace=0,den=0,first=0,firstDen=0,n=0;
        for(var row:body.get("items")) if(row.get("playerId").asInt()==1) { var a=row.at("/metrics/ace_rate");ace+=a.get("numerator").asLong();den+=a.get("denominator").asLong();n+=a.get("observations").asLong();first+=row.at("/metrics/first_serve_points_won/numerator").asLong();firstDen+=row.at("/metrics/first_serve_points_won/denominator").asLong(); }
        assertThat(ace).isEqualTo(5);assertThat(den).isEqualTo(60);assertThat(n).isEqualTo(3);assertThat(first).isEqualTo(23);assertThat(firstDen).isEqualTo(37);
        assertThat(body.at("/sample/population").asLong()).isEqualTo(5);assertThat(body.get("points").isEmpty()).isTrue();
        assertThat(body.get("surfaces").size()).isEqualTo(2);for(var s:body.get("surfaces")) assertThat(s.at("/metrics/ace_rate/value").asDouble()).isCloseTo(.075,within(1e-12));
    }
    @Test void roleServeFlagDoesNotRemoveOpponentAndZeroNumeratorIsReal() throws Exception {
        long run=jdbc.queryForObject("SELECT max(id) FROM import_runs WHERE data_kind='MATCHES'",Long.class);
        long issue=jdbc.queryForObject("INSERT INTO quality_issues(run_id,snapshot_id,source_file_id,record_number,tour,data_kind,rule_code,role,action,reason) SELECT run_id,snapshot_id,source_file_id,record_number,tour,'MATCHES','SERVE_TEST','WINNER','FLAG','fixture' FROM match_sources WHERE run_id=? AND record_number=1 RETURNING id",Long.class,run);jdbc.update("INSERT INTO quality_issue_impacts VALUES(?,'SERVE_STATS')",issue);
        var body=data("");for(var row:body.get("items")) if(row.get("playerId").asInt()==1 && row.get("surface").asText().equals("Hard")) {
            assertThat(row.at("/metrics/ace_rate/value").asDouble()).isZero();assertThat(row.at("/metrics/ace_rate/denominator").asLong()).isEqualTo(10);
        }
        for(var row:body.get("items")) if(row.get("playerId").asInt()==2 && row.get("surface").asText().equals("Hard")) assertThat(row.at("/metrics/ace_rate/numerator").asLong()).isEqualTo(1);
    }
    @Test void cohortCapIsExplicitAndInvalidMetricsAreRejected() throws Exception {
        var body=data("&limit=1");assertThat(body.at("/sample/inspected").asInt()).isEqualTo(1);assertThat(body.at("/sample/omitted").asInt()).isEqualTo(4);
        assertThat(body.at("/sample/population").asLong()).isEqualTo(body.at("/sample/valid").asLong()+body.at("/sample/excluded").asLong()+body.at("/sample/omitted").asLong());
        mvc.perform(get(URL+"&x=DROP_TABLE")).andExpect(status().isBadRequest());mvc.perform(get(URL+"&limit=1001")).andExpect(status().isBadRequest());
    }
}
