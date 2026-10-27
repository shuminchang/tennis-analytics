package com.shuminapp.tennis.ingestion;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;
import org.junit.jupiter.api.Test;
class PlayerConverterTest {
    Map<String,String> row() { return new HashMap<>(Map.of("player_id","1","name_first","First","name_last","Last","hand","R","dob","20000229","ioc","USA","height","180","wikidata_id","Q1")); }
    @Test void preservesValidAndEmptyValues() {
        var raw=row(); raw.put("height",""); raw.put("hand","U");
        var result=new PlayerConverter().convert(raw);
        assertThat(result.player().dob()).hasToString("2000-02-29");
        assertThat(result.player().height()).isNull(); assertThat(result.player().hand()).isEqualTo("U");
        assertThat(result.issues()).isEmpty();
    }
    @Test void invalidIdIsQuarantined() {
        var raw=row();raw.put("player_id","2147483648");
        var result=new PlayerConverter().convert(raw);
        assertThat(result.player()).isNull(); assertThat(result.issues().getFirst().action()).isEqualTo("QUARANTINE");
    }
    @Test void invalidFieldsDoNotDiscardPlayer() {
        var raw=row();raw.put("dob","19000000");raw.put("height","3");raw.put("ioc","?");raw.put("wikidata_id","Q0");raw.put("hand","Z");
        var result=new PlayerConverter().convert(raw);
        assertThat(result.player().id()).isEqualTo(1);assertThat(result.player().dob()).isNull();
        assertThat(result.player().height()).isEqualTo((short)3);assertThat(result.issues()).hasSize(5);
    }
    @Test void distinguishesPlaceholderFromUnknownSubstring() {
        var raw=row();raw.put("name_first","U");raw.put("name_last","Unknown");
        assertThat(new PlayerConverter().convert(raw).issues()).anyMatch(i->i.metric().equals("PLAYER_SEARCH"));
        raw.put("name_first","Ann");raw.put("name_last","Unknown Portland");
        assertThat(new PlayerConverter().convert(raw).issues()).noneMatch(i->i.metric().equals("PLAYER_SEARCH"));
    }
}
