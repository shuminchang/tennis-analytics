package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class H2HTest {
    @AfterEach void cleanup() { FeatureData.clear(jdbc); }
    @Autowired JdbcTemplate jdbc;@Autowired H2HService service;@Autowired AccountService accounts;@Autowired MockMvc mvc;
    @BeforeEach void seed() { FeatureData.seed(jdbc);jdbc.execute("TRUNCATE app_users,audit_log CASCADE");accounts.create("TEST","advanced","fixture-password-123","ADVANCED");accounts.create("TEST","member","fixture-password-123","MEMBER"); }
    @Test void matchesHandCalculationAndZeroSampleInterval() {
        var r=service.between("ATP",1,2,null,null,null);assertThat(r.matches()).isEqualTo(2);assertThat(r.firstWins()).isEqualTo(1);
        assertThat(r.confidence95().lower()).isCloseTo(.094531,within(.00001));assertThat(r.confidence95().upper()).isCloseTo(.905469,within(.00001));
        assertThat(service.between("ATP",1,3,null,null,null).firstWinRate()).isNull();assertThat(H2HRepository.wilson(0,0).lower()).isNull();
    }
    @Test void restrictsRolesAndRejectsInvalidPair() throws Exception {
        mvc.perform(get("/v1/h2h").param("tour","ATP").param("first","1").param("second","2")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/h2h").with(user("member")).param("tour","ATP").param("first","1").param("second","2")).andExpect(status().isForbidden());
        mvc.perform(get("/v1/h2h").with(user("advanced")).param("tour","ATP").param("first","1").param("second","2")).andExpect(status().isOk());
        assertThatThrownBy(()->service.between("ATP",1,1,null,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
