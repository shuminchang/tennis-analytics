package com.shuminapp.tennis.visualization;

import com.fasterxml.jackson.databind.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import com.shuminapp.tennis.auth.AccountService;
import com.shuminapp.tennis.features.FeatureData;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabaseConfiguration.class)
class ResearchWorkbenchTest {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired MockMvc mvc;@Autowired AccountService accounts;
    static final String URL="/v1/visualizations/correlations?tour=ATP&fromYear=2024&toYear=2024";
    @BeforeEach void seed() throws Exception { ResearchFixture.seed(jdbc,json);accounts.create("TEST","researchmember","fixture-password-123","MEMBER");accounts.create("TEST","researchadvanced","fixture-password-123","ADVANCED"); }
    @AfterEach void clear() { FeatureData.clear(jdbc); }
    MockHttpSession login(String user) throws Exception { return (MockHttpSession)mvc.perform(post("/auth/login").with(csrf()).param("username",user).param("password","fixture-password-123")).andExpect(status().isOk()).andReturn().getRequest().getSession(false); }
    @Test void advancedRoleRequiredAndDisableRevokesExistingSession() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());mvc.perform(get(URL).session(login("researchmember"))).andExpect(status().isForbidden());
        var session=login("researchadvanced");var body=json.readTree(mvc.perform(get(URL).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("correlations")).hasSize(36);var height=body.get("correlations").get(0);assertThat(height.get("n").asInt()).isEqualTo(2);assertThat(height.get("pearson").isNull()).isTrue();assertThat(body.at("/hexbin/observations").asInt()).isZero();
        assertThat(body.toString()).doesNotContain("password","/private/archive");jdbc.update("UPDATE app_users SET enabled=false WHERE username='researchadvanced'");mvc.perform(get(URL).session(session)).andExpect(status().isUnauthorized());
    }
    @Test void pairwiseNIsDifferentForSparseMetricsAndCohortIsBounded() throws Exception {
        var session=login("researchadvanced");var body=json.readTree(mvc.perform(get(URL+"&limit=1").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("inspected").asInt()).isEqualTo(1);assertThat(body.get("omitted").asInt()).isEqualTo(4);
        for(var pair:body.get("correlations")) if(!pair.get("x").asText().equals("height_cm") || !pair.get("y").asText().equals("height_cm")) { assertThat(pair.get("n").asInt()).isZero();assertThat(pair.get("spearman").isNull()).isTrue(); }
    }
}
