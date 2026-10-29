package com.shuminapp.tennis.features;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class H2HService {
    private final FeatureSupport support;private final H2HRepository repository;
    public H2HService(FeatureSupport support,H2HRepository repository) { this.support=support;this.repository=repository; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    @org.springframework.cache.annotation.Cacheable("h2h")
    public H2HRepository.Result between(String tour,int first,int second,String from,String to,String surface) {
        if(first==second) throw new IllegalArgumentException();var one=support.player(tour,first);support.player(one.tour(),second);
        LocalDate start=from==null || from.isBlank()?null:LocalDate.parse(from),end=to==null || to.isBlank()?null:LocalDate.parse(to);
        if(start!=null && end!=null && start.isAfter(end)) throw new IllegalArgumentException();
        return repository.between(one.tour(),first,second,start,end,surface==null || surface.isBlank()?null:FeatureSupport.surface(surface));
    }
}
