package com.shuminapp.tennis.player;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {
    private final PlayerRepository repository;
    public PlayerService(PlayerRepository repository) { this.repository = repository; }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=10)
    public PlayerRepository.SearchPage search(String q,String tour,int page,int size) {
        String query = q == null ? "" : q.strip();
        String selected = tour == null || tour.isBlank() ? null : tour.strip().toUpperCase(Locale.ROOT);
        if (query.length()>100 || query.codePoints().anyMatch(Character::isISOControl)
                || (selected!=null && !selected.equals("ATP") && !selected.equals("WTA"))
                || page<0 || page>10000 || size<1 || size>100) {
            throw new IllegalArgumentException("Invalid player search parameters");
        }
        return repository.search(query,selected,page,size);
    }

    @Transactional(readOnly=true, timeout=10)
    public Optional<PlayerRepository.PlayerSummary> findById(String tour,int playerId) {
        String selected = tour == null || tour.isBlank() ? null : tour.strip().toUpperCase(Locale.ROOT);
        if (selected==null || (!selected.equals("ATP") && !selected.equals("WTA")) || playerId<=0) {
            throw new IllegalArgumentException("Invalid player identity");
        }
        return repository.findById(selected,playerId);
    }
}
