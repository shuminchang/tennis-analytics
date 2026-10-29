package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.assertThat;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class StatsTest {
    @Autowired JdbcTemplate jdbc;@Autowired StatsService service;
    @BeforeEach void seed() { FeatureData.seed(jdbc); }
    @Test void excludesUnplayedDefaultsAndUnknownAndIncludesPlayedRetirement() {
        var s=service.summary("atp",1);
        assertThat(s.displayed()).isEqualTo(6);assertThat(s.played()).isEqualTo(2);
        assertThat(s.wins()).isEqualTo(1);assertThat(s.losses()).isEqualTo(1);assertThat(s.winRate()).isEqualTo(.5);
        assertThat(service.summary("ATP",3).winRate()).isNull();
    }
}
