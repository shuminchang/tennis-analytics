package com.shuminapp.tennis.visualization;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.shuminapp.tennis.features.H2HRepository;
import static com.shuminapp.tennis.visualization.ResearchDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
public class ResearchService {
    private final ResearchRepository repository;private final CacheManager caches;
    public ResearchService(ResearchRepository repository,CacheManager caches) { this.repository=repository;this.caches=caches; }
    private record Key(Source source,ResearchRequest request,String version) { }
    <T> T cached(String name,Source source,ResearchRequest r,Supplier<T> load) {
        var cache=caches.getCache(name);return cache==null?load.get():cache.get(new Key(source,r,"research-v1"),load::get);
    }
    public Info info(ResearchRequest r) { var source=repository.source();return cached("researchInfo",source,r,()->repository.info(source,r.tour())); }
    static Context context(String grain,Source s,ResearchRequest r) { return new Context(grain,r.tour(),r.fromYear(),r.toYear(),r.surface(),"research-v1",s,Instant.now()); }
    public Ages ages(ResearchRequest r) {
        var source=repository.source();return cached("researchAge",source,r,()->{
            var rows=repository.ages(source,r);long population=0,valid=0;var reasons=new TreeMap<String,Long>();var bins=new ArrayList<AgeBin>();
            for(var row:rows) { population+=row.n();if(!row.reason().equals("VALID")) { reasons.merge(row.reason(),row.n(),Long::sum);continue; }
                valid+=row.n();var ci=H2HRepository.wilson(row.wins(),row.n());bins.add(new AgeBin(row.bucket(),row.bucket()+r.binWidth(),row.surface(),row.n(),row.wins(),(double)row.wins()/row.n(),ci.lower(),ci.upper(),row.n()<20));
            }
            return new Ages(context("player_age_bin",source,r),r.binWidth(),new Sample(population,valid,population-valid,reasons.entrySet().stream().map(e->new Exclusion(e.getKey(),e.getValue())).toList()),List.copyOf(bins),List.of("SOURCE_AGE_NOT_INDEPENDENTLY_VERIFIED","REPEATED_PLAYERS_NOT_INDEPENDENT","DESCRIPTIVE_NOT_CAUSAL"));
        });
    }
    public static final List<String> METRICS=List.of("height_cm","match_win_rate","ace_rate","double_fault_rate","first_serve_points_won","serve_points_won");
    public Profiles profiles(ResearchRequest r) {
        if(!METRICS.contains(r.x()) || !METRICS.contains(r.y()) || r.x().equals(r.y())) throw new IllegalArgumentException();
        var source=repository.source();return cached("researchProfiles",source,r,()->{
            var selected=repository.profiles(source,r);var points=new ArrayList<ProfilePoint>();var excluded=new TreeMap<String,Long>();
            for(var row:selected.items()) {
                String reason=profileReason(row,r.x(),r.y());if(reason!=null) { excluded.merge(reason,1L,Long::sum);continue; }
                points.add(new ProfilePoint(row.tour(),row.playerId(),row.displayName(),row.season(),row.surface(),row.hand(),row.metrics().get(r.x()).value(),row.metrics().get(r.y()).value(),row.matches()));
            }
            long inspected=selected.items().size();var sample=new ProfileSample(selected.population(),inspected,points.size(),inspected-points.size(),selected.population()-inspected,excluded.entrySet().stream().map(e->new Exclusion(e.getKey(),e.getValue())).toList());
            return new Profiles(context("player_season_surface",source,r),r.x(),r.y(),sample,"MOST_MATCHES_THEN_ID_YEAR_SURFACE",selected.items(),List.copyOf(points),repository.slices(source,r,false),repository.slices(source,r,true),List.of("POST_MATCH_DESCRIPTIVE_ONLY","REPEATED_PLAYERS_NOT_INDEPENDENT","OPPONENT_STRENGTH_AND_ERA_NOT_CONTROLLED","BOUNDED_COHORT_NOT_FULL_POPULATION"));
        });
    }
    static String profileReason(Profile p,String x,String y) {
        for(String key:new TreeSet<>(List.of(x,y))) {
            var m=p.metrics().get(key);if(key.equals("height_cm") && !p.heightReason().equals("VALID")) return key+":"+p.heightReason();
            if(m.value()==null) return key+":MISSING";if(!m.qualified()) return key+":INSUFFICIENT_SAMPLE";
        }
        return null;
    }

}
