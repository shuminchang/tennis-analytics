package com.shuminapp.tennis.visualization;

import java.time.*;
import java.util.*;

public final class VisualizationDtos {
    private VisualizationDtos() { }
    public record Rules(String transformSha, String qualitySha, String scopeSha) { }
    public record Source(String repositoryUrl, String snapshotSha, long playerRunId, long rankingRunId,
                         Long matchRunId, Rules playerRules, Rules rankingRules, String qualityRevision) { }
    public record Context(String grain, String tour, LocalDate rankingDate, int topN, Source source,
                          String metricVersion, String statRuleVersion, String qualityPolicyVersion, Instant generatedAt) { }
    public record Axis(String key, String unit, String direction, String valueType) { }
    public record Axes(Axis x, Axis y) { }
    public record Reason(String metric, String code, int count) { }
    public record Sample(int population, int valid, int excluded, List<Reason> primaryReasons) { }
    public record Point(String tour, int playerId, String displayName, int x, int y, String group) { }
    public record Scatter(Context context, Axes axes, Sample sample, List<Point> items, List<String> warnings) { }
    public record Domain(double min, double max) { }
    public record Bin(double lower, double upper, boolean upperInclusive, int count) { }
    public record Histogram(Context context, String metric, Sample sample, Domain domain, List<Bin> bins, List<String> warnings) { }
    public record AxisPair(String x, String y) { }
    public record Limits(int maxTopN, int maxPoints, int maxBins, int statementTimeoutMs, int cacheSeconds) { }
    public record Metadata(String metricVersion, String tour, List<String> grains, List<AxisPair> axisPairs,
                           List<String> colors, List<String> handGroups, List<String> histogramMetrics,
                           LocalDate earliestRankingDate, LocalDate latestRankingDate, Limits limits,
                           Source source, List<String> warnings) { }
}
