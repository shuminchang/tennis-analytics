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
}
