package com.shuminapp.tennis.features;

import static org.assertj.core.api.Assertions.*;
import com.shuminapp.tennis.TestDatabaseConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE) @Import(TestDatabaseConfiguration.class)
class EloTest {
    @AfterEach void cleanup() { FeatureData.clear(jdbc); }
    @Autowired JdbcTemplate jdbc;@Autowired EloBuildService builder;@Autowired EloService service;
    @BeforeEach void seed() { FeatureData.seed(jdbc);jdbc.execute("TRUNCATE elo_history,elo_current,elo_runs CASCADE"); }
    @Test void startsSymmetricallyAndKDecreases() {
        assertThat(EloMath.expected(1500,1500)).isEqualTo(.5);assertThat(EloMath.k(10)).isLessThan(EloMath.k(0));
        assertThat(EloMath.after(1500,1500,0,true)).isCloseTo(1500+250/Math.pow(5,.4)/2,within(.000001));
    }
    @Test void computesOnlyEligibleMatchesAndReusesIdenticalInputs() {
        var result=builder.rebuild();assertThat(result.matches()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM elo_history WHERE dimension='OVERALL'",Long.class)).isEqualTo(6);
        assertThat(service.history("ATP",1,"OVERALL").items()).hasSize(2);
        assertThat(service.history("ATP",3,"OVERALL").items()).isEmpty();
        assertThat(builder.rebuild().runId()).isEqualTo(result.runId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM elo_runs WHERE status='REUSED'",Long.class)).isEqualTo(1);
    }
    @Test void invalidIdentityRollsBackAndKeepsFailedModel() {
        jdbc.update("UPDATE matches SET loser_id=winner_id WHERE tour='ATP' AND match_num=1");
        assertThatThrownBy(()->builder.rebuild()).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM elo_history",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM elo_runs",String.class)).isEqualTo("FAILED");
    }
}
