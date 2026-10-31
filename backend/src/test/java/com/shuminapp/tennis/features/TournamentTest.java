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
class TournamentTest {
    @AfterEach void cleanup() { FeatureData.clear(jdbc); }
    @Autowired JdbcTemplate jdbc;@Autowired TournamentService service;
    @BeforeEach void seed() { FeatureData.seed(jdbc); }
    @Test void findsReviewedEventAndItsSingleEligibleFinal() {
        var page=service.list("ATP","Fixture",2024,0,20);assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().finalWinner().playerId()).isEqualTo(1);
        var detail=service.detail("ATP","event",0,2).items();assertThat(detail).hasSize(2);
        assertThat(detail.getFirst().get("winner_name")).isEqualTo("Alpha Player");
        assertThat(detail.getFirst().get("loser_name")).isEqualTo("Beta Player");
        jdbc.update("UPDATE matches SET tourney_level='D' WHERE tour='ATP'");FeatureData.refresh(jdbc);
        assertThat(service.list("ATP","",null,0,20).items().getFirst().finalWinner()).isNull();
    }
}
