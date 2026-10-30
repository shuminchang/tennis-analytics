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
}
