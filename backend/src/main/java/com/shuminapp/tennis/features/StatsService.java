package com.shuminapp.tennis.features;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StatsService {
    private final FeatureSupport support;private final StatsRepository stats;
    public StatsService(FeatureSupport support,StatsRepository stats) { this.support=support;this.stats=stats; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    @org.springframework.cache.annotation.Cacheable("summary")
    public StatsRepository.Summary summary(String tour,int id) { var p=support.player(tour,id);return stats.summary(p.tour(),p.playerId()); }
}
