package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import com.shuminapp.tennis.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class AccountTest {
    @Autowired MockMvc mvc;@Autowired AccountService accounts;@Autowired JdbcTemplate jdbc;
    @BeforeEach void seed() { jdbc.execute("TRUNCATE app_users,audit_log CASCADE");accounts.create("TEST","member","fixture-password-123","MEMBER");accounts.create("TEST","admin","fixture-password-123","ADMIN"); }
    MockHttpSession login(String name) throws Exception {
        return (MockHttpSession)mvc.perform(post("/auth/login").with(csrf()).param("username",name).param("password","fixture-password-123")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void protectsCsrfRolesAndLogoutAndDoesNotExposeHashes() throws Exception {
        mvc.perform(get("/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").param("username","member").param("password","fixture-password-123")).andExpect(status().isForbidden());
        var member=login("member");mvc.perform(get("/auth/me").session(member)).andExpect(jsonPath("$.roles[0]").value("MEMBER"));
        mvc.perform(get("/v1/admin/users").session(member)).andExpect(status().isForbidden());
        var admin=login("admin");String body=mvc.perform(get("/v1/admin/users").session(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password_hash","$2a$");
        mvc.perform(post("/auth/logout").session(member).with(csrf())).andExpect(status().isOk());
        assertThat(member.isInvalid()).isTrue();
    }
    @Test void fiveFailuresLockAccountAndDisableTakesEffectOnExistingSession() throws Exception {
        for(int n=0;n<5;n++) mvc.perform(post("/auth/login").with(csrf()).param("username","member").param("password","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").with(csrf()).param("username","member").param("password","fixture-password-123")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT locked_until>clock_timestamp() FROM app_users WHERE username='member'",Boolean.class)).isTrue();
        var admin=login("admin");jdbc.update("UPDATE app_users SET enabled=false WHERE username='admin'");
        mvc.perform(get("/auth/me").session(admin)).andExpect(jsonPath("$.authenticated").value(false));
    }
}
