package com.shuminapp.tennis.features;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TournamentService {
    private final TournamentRepository repository;
    public TournamentService(TournamentRepository repository) { this.repository=repository; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public TournamentRepository.EventPage list(String tour,String q,Integer year,int page,int size) {
        FeatureSupport.year(year);FeatureSupport.page(page,size);if(q.length()>100) throw new IllegalArgumentException();
        return repository.events(FeatureSupport.tour(tour),q.strip(),year,page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public TournamentRepository.EventDetail detail(String tour,String id,int page,int size) {
        FeatureSupport.page(page,size);if(id.isBlank() || id.length()>200) throw new IllegalArgumentException();return repository.detail(FeatureSupport.tour(tour),id,page,size);
    }
}
