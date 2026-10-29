package com.shuminapp.tennis.features;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RankingBrowseService {
    private final FeatureSupport support;private final RankingBrowseRepository repository;
    public RankingBrowseService(FeatureSupport support,RankingBrowseRepository repository) { this.support=support;this.repository=repository; }
    @Transactional(readOnly=true,timeout=10)
    public RankingBrowseRepository.Board board(String tour,String date,int size) { FeatureSupport.page(0,size);return repository.board(FeatureSupport.tour(tour),date==null || date.isBlank()?null:LocalDate.parse(date),size); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public RankingBrowseRepository.Chart chart(String tour,int id) { var p=support.player(tour,id);return repository.chart(p.tour(),p.playerId()); }
    @Transactional(readOnly=true,timeout=10)
    public List<Map<String,Object>> imports() { return repository.imports(); }
}
