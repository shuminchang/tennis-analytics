package com.shuminapp.tennis.visualization;

import java.time.*;
import java.util.*;
import static com.shuminapp.tennis.visualization.VisualizationDtos.*;

public final class ResearchDtos {
    private ResearchDtos() { }
    public record Source(VisualizationDtos.Source playersAndRankings,long matchRunId,Rules matchRules,String qualityRevision,String readModel) { }
    public record Context(String grain,String tour,int fromYear,int toYear,String surface,String metricVersion,Source source,Instant generatedAt) { }
    public record Exclusion(String code,long count) { }
    public record Sample(long population,long valid,long excluded,List<Exclusion> reasons) { }
    public record AgeBin(double lowerAge,double upperAge,String surface,long matches,long wins,Double winRate,Double lower95,Double upper95,boolean lowSample) { }
    public record Ages(Context context,int binWidth,Sample sample,List<AgeBin> items,List<String> warnings) { }
    public record Info(Source source,Integer earliestMatchYear,Integer latestMatchYear,int maxYears,int maxProfiles,List<String> warnings) { }
    public record Measure(Double value,long observations,long numerator,long denominator,boolean qualified) { }
    public record Profile(String tour,int playerId,String displayName,int season,String surface,String hand,long matches,Map<String,Measure> metrics,String heightReason) { }
    public record Slice(String season,String surface,String hand,String opponentHand,long matches,Map<String,Measure> metrics) { }
    public record ProfilePoint(String tour,int playerId,String displayName,int season,String surface,String hand,double x,double y,long matches) { }
    public record ProfileSample(long population,long inspected,long valid,long excluded,long omitted,List<Exclusion> reasons) { }
    public record Profiles(Context context,String x,String y,ProfileSample sample,String selectionRule,List<Profile> items,List<ProfilePoint> points,List<Slice> surfaces,List<Slice> hands,List<String> warnings) { }

    public record Correlation(String x,String y,int n,Double pearson,Double spearman,boolean lowSample) { }
    public record Box(String metric,int n,Double lowerWhisker,Double q1,Double median,Double q3,Double upperWhisker,List<Double> outliers) { }
    public record HexCell(double x,double y,long count,List<List<Double>> vertices) { }
    public record Hexes(int observations,VisualizationDtos.Domain xDomain,VisualizationDtos.Domain yDomain,List<HexCell> cells) { }
    public record Workbench(Context context,List<String> metrics,long population,int inspected,long omitted,String selectionRule,String x,String y,List<Correlation> correlations,List<Box> boxes,Hexes hexbin,List<String> warnings) { }

}
