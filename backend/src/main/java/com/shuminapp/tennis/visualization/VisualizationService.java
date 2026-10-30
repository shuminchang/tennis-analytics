package com.shuminapp.tennis.visualization;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.shuminapp.tennis.visualization.VisualizationDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
public class VisualizationService {
    private static final String VERSION="visualization-v1";
    private final VisualizationRepository repository;
    private final CacheManager caches;
    public VisualizationService(VisualizationRepository repository,CacheManager caches) { this.repository=repository;this.caches=caches; }
    private record Key(Source source,String version,String grain,VisualizationRequest request) { }
    private <T> T cached(String name,Source source,VisualizationRequest request,Class<T> type,Supplier<T> load) {
        var cache=caches.getCache(name);var key=new Key(source,VERSION,"ranking_snapshot",request);
        T value=cache==null?null:cache.get(key,type);if(value!=null) return value;
        value=load.get();if(cache!=null) cache.put(key,value);return value;
    }
    public Metadata metadata(VisualizationRequest r) {
        var s=repository.source();
        return cached("vizMetadata",s,r,Metadata.class,()->new Metadata(VERSION,r.tour(),List.of("ranking_snapshot"),
            List.of(new AxisPair("height_cm","rank"),new AxisPair("rank","height_cm"),new AxisPair("height_cm","ranking_points"),new AxisPair("ranking_points","height_cm")),
            List.of("hand","none"),List.of("R","L","U","A","UNKNOWN"),List.of("height_cm"),
            repository.boundary(s,r.tour(),false),repository.boundary(s,r.tour(),true),new Limits(1000,2000,50,5000,60),s,List.of("SOURCE_ATTRIBUTES_NOT_INDEPENDENTLY_VERIFIED")));
    }
    public Scatter scatter(VisualizationRequest input) {
        var s=repository.source();var r=resolved(s,input);
        return cached("vizScatter",s,r,Scatter.class,()->{
            var rows=cohort(s,r);var items=new ArrayList<Point>();var reasons=new TreeMap<String,Integer>();
            for(var row:rows) {
                String reason=reason(row,List.of(r.x(),r.y()));
                if(reason!=null) { reasons.merge(reason,1,Integer::sum);continue; }
                items.add(new Point(r.tour(),row.playerId(),row.name(),value(row,r.x()),value(row,r.y()),r.color().equals("none")?"ALL":row.hand()==null?"UNKNOWN":row.hand()));
            }
            return new Scatter(context(s,r),new Axes(axis(r.x()),axis(r.y())),sample(rows.size(),items.size(),reasons),List.copyOf(items),warnings(items.size()));
        });
    }
    public Histogram histogram(VisualizationRequest input) {
        var s=repository.source();var r=resolved(s,input);
        return cached("vizHistogram",s,r,Histogram.class,()->{
            var rows=cohort(s,r);var values=new ArrayList<Integer>();var reasons=new TreeMap<String,Integer>();
            for(var row:rows) { String reason=reason(row,List.of("height_cm"));if(reason!=null) reasons.merge(reason,1,Integer::sum);else values.add(row.height()); }
            Domain domain=null;List<Bin> bins=List.of();
            if(!values.isEmpty()) {
                int min=Collections.min(values),max=Collections.max(values);domain=new Domain(min,max);
                if(min==max) bins=List.of(new Bin(min-0.5,max+0.5,true,values.size()));
                else {
                    int[] counts=new int[r.bins()];
                    // Integer bin assignment avoids rounding a boundary into the previous bin.
                    for(int v:values) counts[Math.min(r.bins()-1,(v-min)*r.bins()/(max-min))]++;
                    var result=new ArrayList<Bin>();double width=(double)(max-min)/r.bins();
                    for(int i=0;i<counts.length;i++) result.add(new Bin(min+i*width,i==counts.length-1?max:min+(i+1)*width,i==counts.length-1,counts[i]));
                    bins=List.copyOf(result);
                }
            }
            return new Histogram(context(s,r),"height_cm",sample(rows.size(),values.size(),reasons),domain,bins,warnings(values.size()));
        });
    }
    private VisualizationRequest resolved(Source s,VisualizationRequest r) {
        return new VisualizationRequest(r.tour(),r.date()==null?repository.boundary(s,r.tour(),true):r.date(),r.topN(),r.x(),r.y(),r.color(),r.bins());
    }
    private List<VisualizationRepository.Row> cohort(Source s,VisualizationRequest r) {
        var rows=repository.cohort(s,r.tour(),r.date(),r.topN());if(rows.size()>2000) throw new IllegalArgumentException();return rows;
    }
    private static Integer value(VisualizationRepository.Row r,String key) {
        return switch(key) { case "height_cm"->r.height();case "rank"->r.rank();case "ranking_points"->r.points();default->throw new IllegalArgumentException(); };
    }
    private static String reason(VisualizationRepository.Row r,List<String> metrics) {
        for(String key:new TreeSet<>(metrics)) {
            if(key.equals("height_cm") && r.blocked()) return key+":QUALITY_BLOCKED";
            Integer v=value(r,key);if(v==null) return key+":MISSING";
            if(key.equals("height_cm") && (v<100 || v>250) || key.equals("rank") && v<1 || key.equals("ranking_points") && v<0) return key+":OUT_OF_RANGE";
        }
        return null;
    }
    private static Sample sample(int population,int valid,Map<String,Integer> reasons) {
        return new Sample(population,valid,population-valid,reasons.entrySet().stream().map(e->{String[] parts=e.getKey().split(":");return new Reason(parts[0],parts[1],e.getValue());}).toList());
    }
    private static Axis axis(String key) { return new Axis(key,key.equals("height_cm")?"cm":key.equals("rank")?"rank":"points",key.equals("rank")?"descending":"ascending","integer"); }
    private static Context context(Source s,VisualizationRequest r) { return new Context("ranking_snapshot",r.tour(),r.date(),r.topN(),s,VERSION,"stats-v1",VERSION,Instant.now()); }
    private static List<String> warnings(int n) { return n<5?List.of("LOW_SAMPLE","SOURCE_ATTRIBUTES_NOT_INDEPENDENTLY_VERIFIED"):List.of("SOURCE_ATTRIBUTES_NOT_INDEPENDENTLY_VERIFIED"); }
}
