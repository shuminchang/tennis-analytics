package com.shuminapp.tennis.prediction;

import com.fasterxml.jackson.databind.*;
import com.shuminapp.tennis.visualization.ResearchDtos;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class MatchupModel {
    private final JsonNode artifact;private final ResearchDtos.Source source;
    public MatchupModel(ObjectMapper mapper,ResourceLoader loader,@Value("${app.matchup-model:classpath:prediction/matchup-v1.json}") String location) {
        var resource=loader.getResource(location);
        try {
            if(!resource.exists()) {artifact=null;source=null;return;}
            try(var in=resource.getInputStream()) {byte[] bytes=in.readNBytes(4_000_001);if(bytes.length>4_000_000)throw new IllegalArgumentException();artifact=mapper.readTree(bytes);}
            if(artifact.path("schema").asInt()!=1 || artifact.path("embargoDays").asInt()!=21 || !artifact.path("version").asText().equals("matchup-v1"))throw new IllegalArgumentException();
            source=mapper.treeToValue(artifact.get("source"),ResearchDtos.Source.class);
            for(var tour:artifact.path("models")) for(var fold:tour) {
                coefficients(fold,"eloCoefficients");if(!fold.path("augmentedCoefficients").isNull())coefficients(fold,"augmentedCoefficients");
                if(!LocalDate.parse(fold.path("validationThrough").asText()).plusDays(21).isBefore(LocalDate.parse(fold.path("availableFrom").asText())))throw new IllegalArgumentException();
            }
        } catch(Exception e) {throw new IllegalStateException("Invalid matchup model resource",e);}
    }
    public boolean compatible(ResearchDtos.Source actual,MatchupRepository.EloRun elo) {
        return artifact!=null && source.equals(actual) && artifact.path("eloInputSha").asText().equals(elo.inputSha()) && artifact.path("eloRulesSha").asText().equals(elo.rulesSha());
    }
    public JsonNode fold(String tour,LocalDate asOf) {
        if(artifact==null)return null;JsonNode latest=null;
        for(var fold:artifact.path("models").path(tour))if(!LocalDate.parse(fold.path("availableFrom").asText()).isAfter(asOf))latest=fold;
        return latest;
    }
    public static double[] coefficients(JsonNode fold,String field) {
        var values=fold.path(field);if(!values.isArray() || values.size()!=6)throw new IllegalArgumentException("Invalid coefficients");
        double[] out=new double[6];for(int i=0;i<6;i++){if(!values.get(i).isNumber() || !Double.isFinite(values.get(i).asDouble()))throw new IllegalArgumentException();out[i]=values.get(i).asDouble();}return out;
    }
}
