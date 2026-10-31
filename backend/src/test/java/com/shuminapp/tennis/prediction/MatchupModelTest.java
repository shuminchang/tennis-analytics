package com.shuminapp.tennis.prediction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuminapp.tennis.visualization.ResearchDtos;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
class MatchupModelTest {
    @Test void realArtifactIsBoundToSourceAndOnlyUsesEarlierYears()throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();var location="classpath:prediction/matchup-v1.json";
        var raw=json.readTree(getClass().getResourceAsStream("/prediction/matchup-v1.json"));var store=new MatchupModel(json,new DefaultResourceLoader(),location);
        var source=json.treeToValue(raw.get("source"),ResearchDtos.Source.class);var elo=new MatchupRepository.EloRun(1,raw.path("eloRulesSha").asText(),raw.path("eloInputSha").asText());
        assertThat(store.compatible(source,elo)).isTrue();assertThat(store.compatible(new ResearchDtos.Source(source.playersAndRankings(),source.matchRunId(),source.matchRules(),"changed",source.readModel()),elo)).isFalse();
        var fold=store.fold("ATP",LocalDate.of(2024,1,1));assertThat(fold.path("year").asInt()).isEqualTo(2024);
        assertThat(LocalDate.parse(fold.path("validationThrough").asText()).plusDays(21)).isBefore(LocalDate.of(2024,1,1));
        assertThat(fold.has("heldOut")).isFalse();assertThat(store.fold("ATP",LocalDate.of(2000,1,1))).isNull();
    }
}
