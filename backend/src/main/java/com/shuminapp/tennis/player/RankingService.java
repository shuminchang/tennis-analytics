package com.shuminapp.tennis.player;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingService {
    private final PlayerService players;
    private final RankingRepository rankings;
    public RankingService(PlayerService players,RankingRepository rankings) { this.players=players;this.rankings=rankings; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=10)
    public Optional<RankingRepository.History> history(String tour,int playerId,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw new IllegalArgumentException("Invalid ranking pagination");
        return players.findById(tour,playerId).map(p->rankings.history(p.tour(),p.playerId(),page,size));
    }
}
