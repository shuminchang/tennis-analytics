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
class ResearchAgeTest {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    @BeforeEach void seed() throws Exception { ResearchFixture.seed(jdbc,json); }
    @AfterEach void clear() { FeatureData.clear(jdbc); }
    @Test void handCalculatedDoublePerspectivesRetirementsAndRoleAgeExclusion() throws Exception {
        var body=json.readTree(mvc.perform(get("/v1/visualizations/age-performance?tour=ATP&fromYear=2024&toYear=2024")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.at("/sample/population").asLong()).isEqualTo(6);assertThat(body.at("/sample/valid").asLong()).isEqualTo(5);assertThat(body.at("/sample/excluded").asLong()).isEqualTo(1);
        var first=body.get("items").get(0);assertThat(first.get("lowerAge").asDouble()).isEqualTo(20);assertThat(first.get("matches").asLong()).isEqualTo(2);assertThat(first.get("wins").asLong()).isEqualTo(1);
        assertThat(first.get("lower95").asDouble()).isCloseTo(.09453120573423074,within(1e-12));assertThat(first.get("upper95").asDouble()).isCloseTo(.9054687942657693,within(1e-12));
        assertThat(body.at("/context/source/readModel").asText()).isEqualTo("v_stat_matches-live");assertThat(body.toString()).doesNotContain("/private/archive");
    }
    @Test void emptyWindowsSurfaceGroupingAndQualityResolution() throws Exception {
        mvc.perform(get("/v1/visualizations/age-performance?tour=ATP&fromYear=2023&toYear=2023")).andExpect(status().isOk()).andExpect(jsonPath("$.sample.population").value(0)).andExpect(jsonPath("$.items").isEmpty());
        jdbc.update("UPDATE quality_issues SET status='RESOLVED',resolution_note='fixture',resolved_at=clock_timestamp() WHERE rule_code='AGE_ANALYSIS'");
        mvc.perform(get("/v1/visualizations/age-performance?tour=ATP&fromYear=2024&toYear=2024&surface=Hard&group=surface&binWidth=5")).andExpect(status().isOk()).andExpect(jsonPath("$.sample.valid").value(4)).andExpect(jsonPath("$.items[0].surface").value("Hard"));
    }
    @Test void boundedParametersAndCrossTourSourceInfo() throws Exception {
        for(String q:new String[]{"tour=ATP&fromYear=2000&toYear=2025","tour=ATP&fromYear=2024","tour=ATP&fromYear=2024&toYear=2024&binWidth=2","tour=ATP&fromYear=2024&toYear=2024&sql=x"}) mvc.perform(get("/v1/visualizations/age-performance?"+q)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/visualizations/age-performance?tour=WTA&fromYear=2024&toYear=2024")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/v1/visualizations/research-metadata?tour=ATP")).andExpect(status().isOk()).andExpect(jsonPath("$.latestMatchYear").value(2024));
    }
}
