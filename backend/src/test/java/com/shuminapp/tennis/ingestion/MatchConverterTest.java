package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MatchConverterTest {
    private final MatchConverter converter=new MatchConverter();
    private Map<String,String> row() { return new HashMap<>(Map.of("tourney_id","test","round","F","match_num","1","tourney_date","20240229","winner_id","1","loser_id","2")); }
    @Test void preservesTextSeedsZeroNegativeStatsAndExactAge() {
        var raw=row();raw.putAll(Map.of("winner_seed","1F","minutes","0","winner_age","0.0","w_ace","0","l_bpSaved","-4","winner_ht","3","score","7-Oct"));
        var c=converter.convert("ATP",raw);
        assertThat(c.error()).isNull();assertThat(c.fields().get("winner_seed")).isEqualTo("1F");
        assertThat(c.fields().get("minutes")).isEqualTo(0);assertThat(c.fields().get("w_ace")).isEqualTo(0);
        assertThat(c.fields().get("l_bp_saved")).isEqualTo(-4);assertThat(c.fields().get("winner_height_cm")).isEqualTo(3);
        assertThat(c.fields().get("score")).isEqualTo("7-Oct");assertThat(c.issues()).hasSize(5);
    }
    @ParameterizedTest @ValueSource(strings={"20230229","00000101","2024107",""})
    void rejectsInvalidRequiredDate(String date) { var raw=row();raw.put("tourney_date",date);assertThat(converter.convert("ATP",raw).error()).isEqualTo("INVALID_EVENT_DATE"); }
    @Test void optionalUnrepresentableValuesDoNotDiscardTheMatch() {
        var raw=row();raw.putAll(Map.of("draw_size","exho","winner_age","21.15","loser_rank","-1","minutes","32768","best_of","2"));
        var c=converter.convert("ATP",raw);assertThat(c.error()).isNull();
        for(String field:List.of("draw_size","winner_age","loser_rank","minutes","best_of")) assertThat(c.fields().get(field)).isNull();
        assertThat(c.issues()).hasSize(5);
    }
    @Test void flagsServeRelationsAndUnknownRoundWithoutDroppingResult() {
        var raw=row();raw.putAll(Map.of("round","NEW","w_svpt","10","w_1stIn","12","l_bpSaved","5","l_bpFaced","4"));
        var c=converter.convert("WTA",raw);assertThat(c.error()).isNull();assertThat(c.issues()).hasSize(3);
    }
    @Test void identityFlagsAreDistinctFromOtherFieldIssues() {
        var raw=row();raw.put("loser_id","1");raw.put("loser_name","U Unknown");
        assertThat(converter.convert("ATP",raw).issues().stream().map(MatchConverter.Issue::metric)).containsOnly("MATCH_HISTORY");
        assertThat(MatchConverter.placeholder("Ann Unknown Portland")).isFalse();
    }
    @Test void scopeExceptionsAndDefaultsAreAppliedWithoutGuessing() {
        var scope=new TournamentScope();
        assertThat(scope.classify("ATP","2023-0605","A").category()).isEqualTo("ATP_FINALS");
        assertThat(scope.classify("ATP","2017-7696","F").status()).isEqualTo("EXCLUDE");
        assertThat(scope.classify("WTA","2024-2052","W").status()).isEqualTo("EXCLUDE");
        assertThat(scope.classify("WTA","history","W").status()).isEqualTo("REVIEW");
        assertThat(scope.classify("ATP","team","D").category()).isEqualTo("TEAM_SINGLES");
        assertThrows(IllegalStateException.class,()->scope.classify("ATP","2023-0605","F"));
    }
}
