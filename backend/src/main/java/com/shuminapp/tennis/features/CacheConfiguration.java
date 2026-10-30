package com.shuminapp.tennis.features;

import java.time.Duration;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.*;

@Configuration @EnableCaching
public class CacheConfiguration {
    @Bean public CacheManager cacheManager() {
        var manager=new CaffeineCacheManager("summary","analysis","events","board","h2h");
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(256).expireAfterWrite(Duration.ofSeconds(60)).recordStats());
        for(String name:new String[]{"vizMetadata","vizScatter","vizHistogram"}) manager.registerCustomCache(name,Caffeine.newBuilder().maximumSize(128).expireAfterWrite(Duration.ofSeconds(60)).recordStats().build());
        return manager;
    }
}
