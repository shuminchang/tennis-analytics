package com.shuminapp.tennis.player;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {
    private final PlayerService players;
    private final MatchRepository matches;
    public MatchService(PlayerService players,MatchRepository matches) { this.players=players;this.matches=matches; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public Optional<MatchRepository.History> history(String tour,int playerId,Integer year,int page,int size) {
        if(year!=null && (year<1 || year>9998) || page<0 || page>10000 || size<1 || size>100) throw new IllegalArgumentException("Invalid match query");
        return players.findById(tour,playerId).map(p->matches.history(p.tour(),p.playerId(),year,page,size));
    }
}
