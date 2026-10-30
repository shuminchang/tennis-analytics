package com.shuminapp.tennis.visualization;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ResearchLifecycleTest {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired MockMvc mvc;@Autowired ResearchRepository repository;@Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    static final String URL="/v1/visualizations/age-performance?tour=ATP&fromYear=2024&toYear=2024";
    @BeforeEach void seed() throws Exception { ResearchFixture.seed(jdbc,json); }
    @AfterEach void clear() { FeatureData.clear(jdbc); }
    com.fasterxml.jackson.databind.JsonNode data() throws Exception { return json.readTree(mvc.perform(get(URL)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    @Test void actualCacheInvalidatesOnQualityAndScopeChanges() throws Exception {
        var service=new ResearchService(repository,new com.shuminapp.tennis.features.CacheConfiguration().cacheManager());
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var request=new ResearchRequest("ATP",2024,2024,"ALL",1,"overall",500,"height_cm","ace_rate");
        var cached=tx.execute(s->service.ages(request));var again=tx.execute(s->service.ages(request));assertThat(again).isSameAs(cached);
        var first=data();
        jdbc.update("UPDATE quality_issues SET status='RESOLVED',resolution_note='test',resolved_at=clock_timestamp() WHERE rule_code='AGE_ANALYSIS'");
        var fresh=tx.execute(s->service.ages(request));assertThat(fresh.sample().valid()).isEqualTo(6);assertThat(fresh).isNotSameAs(cached);
        var resolved=data();assertThat(resolved.at("/sample/valid").asInt()).isEqualTo(6);assertThat(resolved.at("/context/source/qualityRevision")).isNotEqualTo(first.at("/context/source/qualityRevision"));
        jdbc.update("UPDATE tournament_decisions SET scope_status='REVIEW'");
        assertThat(tx.execute(s->service.ages(request)).sample().population()).isZero();
        var scoped=data();assertThat(scoped.at("/sample/population").asInt()).isZero();assertThat(scoped.at("/context/source/qualityRevision")).isNotEqualTo(resolved.at("/context/source/qualityRevision"));
    }
    @Test void runningOrMissingMatchesCannotUseWarmResult() throws Exception {
        data();jdbc.update("UPDATE import_runs SET status='RUNNING',finished_at=NULL WHERE data_kind='MATCHES'");
        mvc.perform(get(URL)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("UNAVAILABLE"));
        jdbc.update("UPDATE import_runs SET status='FAILED',finished_at=clock_timestamp(),error_summary='fixture' WHERE data_kind='MATCHES'");
        mvc.perform(get(URL)).andExpect(status().isServiceUnavailable());
    }
    @Test void duplicateBlankAndOverwideRequestsAreRejected() throws Exception {
        for(String suffix:new String[]{"&tour=WTA","&surface=","&fromYear=2023","&group=DROP_TABLE"}) mvc.perform(get(URL+suffix)).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/visualizations/age-performance?tour=ATP&fromYear=2014&toYear=2024")).andExpect(status().isBadRequest());
    }
}
