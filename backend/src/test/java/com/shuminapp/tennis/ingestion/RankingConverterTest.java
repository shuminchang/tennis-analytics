package com.shuminapp.tennis.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RankingConverterTest {
    private final RankingConverter converter=new RankingConverter();
    private Map<String,String> row() { return new HashMap<>(Map.of("ranking_date","20240229","player","1","rank","2","points","0","tours","")); }
    @Test void preservesZeroAndMissingAndAtpTours() {
        var raw=row();var c=converter.convert("ATP",raw);
        assertThat(c.error()).isNull();assertThat(c.points()).isZero();assertThat(c.tours()).isNull();
        raw.put("points","");raw.put("tours","0");c=converter.convert("WTA",raw);
        assertThat(c.error()).isNull();assertThat(c.points()).isNull();assertThat(c.tours()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"20230229","00000101","2024107","20241301","","２０２４０１０１"})
    void rejectsInvalidDates(String date) {
        var raw=row();raw.put("ranking_date",date);
        assertThat(converter.convert("ATP",raw).error()).isEqualTo("INVALID_DATE");
    }
    @ParameterizedTest @ValueSource(strings={"-1","2147483648","1.5"," 1","x"})
    void rejectsInvalidNumbersWithoutTreatingThemAsMissing(String text) {
        for(String field:List.of("player","rank","points","tours")) {
            var raw=row();raw.put(field,text);assertThat(converter.convert("WTA",raw).error()).isNotNull();
        }
    }
    @Test void rejectsZeroOrMissingRequiredValues() {
        for(String field:List.of("player","rank")) for(String text:List.of("0","")) {
            var raw=row();raw.put(field,text);assertThat(converter.convert("ATP",raw).error()).isNotNull();
        }
    }
}
