package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabaseConfiguration.class)
class AnalysisTest {
    @AfterEach void cleanup() { FeatureData.clear(jdbc); }
    @Autowired JdbcTemplate jdbc;@Autowired AnalysisService service;
    @BeforeEach void seed() { FeatureData.seed(jdbc); }
    @Test void weightsServePointsAndKeepsMetricSpecificSamples() {
        var row=service.breakdown("ATP",1,"surface").getFirst();assertThat(row.matches()).isEqualTo(2);
        assertThat(row.serveRate()).isCloseTo(72d/110,within(.000001));assertThat(row.serveSamples()).isEqualTo(2);
        assertThat(row.aceSamples()).isEqualTo(1);assertThat(service.breakdown("ATP",3,"year")).isEmpty();
        jdbc.update("UPDATE matches SET w_svpt=0,l_svpt=0");assertThat(service.breakdown("ATP",1,"surface").getFirst().serveRate()).isNull();
    }
}
