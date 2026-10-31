package com.shuminapp.tennis.prediction;

import com.fasterxml.jackson.databind.JsonNode;
import com.shuminapp.tennis.features.FeatureSupport;
import com.shuminapp.tennis.visualization.ResearchRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.shuminapp.tennis.prediction.MatchupRepository.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
public class MatchupService {
    private final ResearchRepository sources;private final MatchupRepository repository;private final MatchupModel models;private final FeatureSupport players;
    public MatchupService(ResearchRepository sources,MatchupRepository repository,MatchupModel models,FeatureSupport players) {this.sources=sources;this.repository=repository;this.models=models;this.players=players;}
    public record Metadata(String tour,LocalDate sourceLastDate,LocalDate suggestedDate,LocalDate maxDate,int embargoDays,String snapshot,String version) { }
    public Metadata metadata(String rawTour) {
        String tour=FeatureSupport.tour(rawTour);var source=sources.source();var run=repository.run();if(!models.compatible(source,run))throw new Unavailable();var latest=repository.latest(run,tour);
        return new Metadata(tour,latest,latest==null?null:latest.plusDays(22),latest==null?null:latest.plusDays(366),21,source.playersAndRankings().snapshotSha(),"matchup-v1");
    }
    public record Estimate(String method,Double firstProbability,Double secondProbability,JsonNode validation) { }
    public record Contribution(String feature,double value,double coefficient,double logOdds) { }
    public record ModelInfo(Integer year,String trainThrough,String validationThrough,String selected,JsonNode validation) { }
    public record Result(boolean available,String reason,String tour,LocalDate asOf,LocalDate historyBefore,LocalDate sourceLastDate,String surface,int bestOf,String snapshot,Inputs first,Inputs second,Estimate prediction,List<Estimate> comparisons,ModelInfo model,List<Contribution> contributions,List<String> warnings) { }
    public Result predict(String rawTour,int first,int second,LocalDate asOf,String rawSurface,int bestOf) {
        if(first<=0 || second<=0 || first==second || asOf==null || asOf.getYear()<1900 || asOf.getYear()>2100 || (bestOf!=3 && bestOf!=5))throw new IllegalArgumentException();
        String tour=FeatureSupport.tour(rawTour),surface=FeatureSupport.surface(rawSurface);if(surface==null || tour.equals("WTA") && bestOf!=3)throw new IllegalArgumentException();
        var one=players.player(tour,first);var two=players.player(tour,second);var source=sources.source();var run=repository.run();
        if(!models.compatible(source,run))throw new Unavailable();var latest=repository.latest(run,tour);
        if(latest!=null && asOf.isAfter(latest.plusDays(366)))throw new IllegalArgumentException();
        var a=repository.inputs(run,tour,first,String.join(" ",List.of(Objects.toString(one.nameFirst(),""),Objects.toString(one.nameLast(),""))).strip(),surface,asOf,source.matchRunId());
        var b=repository.inputs(run,tour,second,String.join(" ",List.of(Objects.toString(two.nameFirst(),""),Objects.toString(two.nameLast(),""))).strip(),surface,asOf,source.matchRunId());
        var warnings=new ArrayList<>(List.of("EXPERIMENTAL_NOT_OFFICIAL","TOURNAMENT_DATE_APPROXIMATION_EMBARGO21","NO_INJURY_FITNESS_OR_LIVE_DATA","OBSERVED_AGE_PROJECTED_NOT_DOB","SNAPSHOT_RETROSPECTIVE_QUALITY"));
        if(!MatchupMath.experienced(a,asOf) || !MatchupMath.experienced(b,asOf))return new Result(false,"INSUFFICIENT_RECENT_HISTORY",tour,asOf,asOf.minusDays(21),latest,surface,bestOf,source.playersAndRankings().snapshotSha(),a,b,null,List.of(),null,List.of(),warnings);
        var x=MatchupMath.features(a,b,asOf,bestOf);var fold=models.fold(tour,asOf);double[] base=fold==null?MatchupMath.STANDARD:MatchupModel.coefficients(fold,"eloCoefficients");double[] selected=base;String method=fold==null?"STANDARD_ELO":fold.path("fallback").asText();
        var comparisons=new ArrayList<Estimate>();comparisons.add(estimate("STANDARD_ELO",x,MatchupMath.STANDARD,fold==null?null:fold.path("validation").path("baseline")));
        if(!method.equals("STANDARD_ELO"))comparisons.add(estimate(method,x,base,fold.path("validation").path("elo")));
        boolean complete=MatchupMath.physical(a) && MatchupMath.physical(b);
        if(fold!=null && !fold.path("augmentedCoefficients").isNull()) {
            var aug=MatchupModel.coefficients(fold,"augmentedCoefficients");
            if(complete){comparisons.add(estimate("ELO_AGE_HEIGHT",x,aug,fold.path("validation").path("augmentedCompleteCases")));if(fold.path("selected").asText().equals("ELO_AGE_HEIGHT")){selected=aug;method="ELO_AGE_HEIGHT";}}
        }
        if(!complete)warnings.add("AGE_OR_HEIGHT_MISSING_ELO_FALLBACK");if(fold==null || fold.path("validation").path("selected").path("n").asInt()<200)warnings.add("UNCALIBRATED_SMALL_SAMPLE");
        if(method.equals("STANDARD_ELO"))warnings.add("STANDARD_ELO_NOT_FORMAT_CALIBRATED");if(latest!=null && asOf.isAfter(latest.plusDays(22)))warnings.add("SOURCE_NOT_LIVE");
        var terms=new ArrayList<Contribution>();String[] names={"overall_elo_delta_400","surface_elo_delta_400","age_delta_10","age_squared_delta_100","height_delta_10","best_of_five_elo_delta_400"};for(int i=0;i<6;i++)terms.add(new Contribution(names[i],x[i],selected[i],x[i]*selected[i]));
        var model=fold==null?null:new ModelInfo(fold.path("year").asInt(),fold.path("trainThrough").asText(),fold.path("validationThrough").asText(),fold.path("selected").asText(),fold.path("validation"));
        return new Result(true,null,tour,asOf,asOf.minusDays(21),latest,surface,bestOf,source.playersAndRankings().snapshotSha(),a,b,estimate(method,x,selected,fold==null?null:fold.path("validation").path(method.equals("ELO_AGE_HEIGHT")?"selected":"elo")),List.copyOf(comparisons),model,List.copyOf(terms),List.copyOf(warnings));
    }
    private static Estimate estimate(String method,double[] x,double[] beta,JsonNode validation) {double p=MatchupMath.probability(x,beta);return new Estimate(method,p,1-p,validation);}
}
