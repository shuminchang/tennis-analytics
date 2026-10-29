package com.shuminapp.tennis.features;

import java.util.*;
import com.shuminapp.tennis.player.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MemberService {
    private final FeatureSupport support;private final StatsRepository stats;private final MemberRepository members;
    public MemberService(FeatureSupport support,StatsRepository stats,MemberRepository members) { this.support=support;this.stats=stats;this.members=members; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public List<Comparison> compare(String tour,String ids,Integer year,String surface) {
        FeatureSupport.year(year);surface=surface==null || surface.isBlank()?null:FeatureSupport.surface(surface);
        if(ids.length()>100) throw new IllegalArgumentException();var values=Arrays.stream(ids.split(",")).map(Integer::parseInt).toList();
        if(values.size()<2 || values.size()>4 || new HashSet<>(values).size()!=values.size()) throw new IllegalArgumentException();
        List<Comparison> result=new ArrayList<>();for(int id:values) { var p=support.player(tour,id);result.add(new Comparison(p,stats.summary(p.tour(),id,year,surface))); }return result;
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> favorites(String username) { return members.favorites(username); }
    @Transactional public void add(String username,String tour,int id) { var p=support.player(tour,id);members.add(username,p.tour(),id); }
    @Transactional public void remove(String username,String tour,int id) { if(id<=0) throw new IllegalArgumentException();members.remove(username,FeatureSupport.tour(tour),id); }
    public record Comparison(PlayerRepository.PlayerSummary player,StatsRepository.Summary summary) { }
}
