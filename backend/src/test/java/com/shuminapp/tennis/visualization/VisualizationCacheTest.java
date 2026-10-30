package com.shuminapp.tennis.visualization;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import com.shuminapp.tennis.features.CacheConfiguration;
import static com.shuminapp.tennis.visualization.VisualizationDtos.*;

class VisualizationCacheTest {
    Source source(String revision) { var rules=new Rules("a","b","c");return new Source("https://example.com","d".repeat(40),1,2,null,rules,rules,revision); }
    @Test void actualBoundedCacheReusesButQualityRevisionAndClearInvalidate() {
        var repo=mock(VisualizationRepository.class);var caches=new CacheConfiguration().cacheManager();var service=new VisualizationService(repo,caches);
        var date=LocalDate.of(2024,1,1);var one=source("one");var two=source("two");
        when(repo.source()).thenReturn(one);when(repo.boundary(one,"ATP",true)).thenReturn(date);
        when(repo.cohort(one,"ATP",date,100)).thenReturn(List.of(new VisualizationRepository.Row(1,"Fixture",180,"R",1,0,false)));
        var request=new VisualizationRequest("ATP",null,100,"height_cm","rank","hand",20);
        assertThat(service.scatter(request).items()).hasSize(1);service.scatter(request);
        verify(repo,times(1)).cohort(one,"ATP",date,100);
        when(repo.source()).thenReturn(two);when(repo.boundary(two,"ATP",true)).thenReturn(date);
        when(repo.cohort(two,"ATP",date,100)).thenReturn(List.of(new VisualizationRepository.Row(1,"Fixture",180,"R",1,0,true)));
        assertThat(service.scatter(request).sample().valid()).isZero();verify(repo).cohort(two,"ATP",date,100);
        for(String name:caches.getCacheNames()) caches.getCache(name).clear();service.scatter(request);verify(repo,times(2)).cohort(two,"ATP",date,100);
        var nativeCache=((CaffeineCache)caches.getCache("vizScatter")).getNativeCache();
        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(128);
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter()).isEqualTo(Duration.ofSeconds(60));
    }
    @Test void aNewSourceFailureCannotServePreviouslyCachedResults() {
        var repo=mock(VisualizationRepository.class);var service=new VisualizationService(repo,new CacheConfiguration().cacheManager());
        when(repo.source()).thenReturn(source("one")).thenThrow(new VisualizationRepository.NotReady());
        var request=new VisualizationRequest("ATP",LocalDate.of(2024,1,1),100,"height_cm","rank","hand",20);
        when(repo.cohort(any(),any(),any(),anyInt())).thenReturn(List.of());service.scatter(request);
        assertThatThrownBy(()->service.scatter(request)).isInstanceOf(VisualizationRepository.NotReady.class);
    }
}
