package com.shuminapp.tennis.features;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AnalysisService {
    private final FeatureSupport support;private final AnalysisRepository repository;
    public AnalysisService(FeatureSupport support,AnalysisRepository repository) { this.support=support;this.repository=repository; }
    @Transactional(readOnly=true,timeout=10)
    public List<AnalysisRepository.Breakdown> breakdown(String tour,int id,String by) {
        if(!Set.of("surface","year").contains(by)) throw new IllegalArgumentException();var p=support.player(tour,id);return repository.breakdown(p.tour(),p.playerId(),by);
    }
}
