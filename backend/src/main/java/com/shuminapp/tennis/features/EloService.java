package com.shuminapp.tennis.features;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EloService {
    private final FeatureSupport support;private final EloRepository repository;
    public EloService(FeatureSupport support,EloRepository repository) { this.support=support;this.repository=repository; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public EloRepository.History history(String tour,int id,String dimension) {
        if(!Set.of("OVERALL","Hard","Clay","Grass","Carpet").contains(dimension)) throw new IllegalArgumentException();var p=support.player(tour,id);return repository.history(p.tour(),id,dimension);
    }
}
