package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import com.shuminapp.tennis.auth.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @Import(TestDatabaseConfiguration.class)
class MemberTest {
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired AccountService accounts;@Autowired MemberService service;
    @BeforeEach void seed() { FeatureData.seed(jdbc);jdbc.execute("TRUNCATE app_users,audit_log CASCADE");accounts.create("TEST","one","fixture-password-123","MEMBER");accounts.create("TEST","two","fixture-password-123","MEMBER"); }
    @Test void favoritesBelongOnlyToTheCurrentMemberAndAreIdempotent() throws Exception {
        mvc.perform(get("/v1/favorites")).andExpect(status().isUnauthorized());
        for(int i=0;i<2;i++) mvc.perform(post("/v1/favorites").with(user("one")).with(csrf()).contentType("application/json").content("{\"tour\":\"ATP\",\"playerId\":1}")).andExpect(status().isOk());
        mvc.perform(get("/v1/favorites").with(user("one"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/v1/favorites").with(user("two"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/v1/favorites/ATP/1").with(user("two")).with(csrf())).andExpect(status().isOk());
        assertThat(service.favorites("one")).hasSize(1);
    }
    @Test void comparesDistinctPlayersUsingIdenticalFilters() throws Exception {
        mvc.perform(get("/v1/compare").with(user("one")).param("tour","ATP").param("ids","1,2").param("year","2024").param("surface","Hard"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].summary.played").value(2)).andExpect(jsonPath("$[1].summary.played").value(2));
        mvc.perform(get("/v1/compare").with(user("one")).param("tour","ATP").param("ids","1,1")).andExpect(status().isBadRequest());
    }
}
