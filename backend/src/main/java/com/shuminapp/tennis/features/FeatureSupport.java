package com.shuminapp.tennis.features;

import com.shuminapp.tennis.player.PlayerRepository;
import com.shuminapp.tennis.player.PlayerService;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FeatureSupport {
    private final PlayerService players;
    public FeatureSupport(PlayerService players) { this.players=players; }
    public PlayerRepository.PlayerSummary player(String tour,int id) { return players.findById(tour,id).orElseThrow(NoSuchElementException::new); }
    public static String tour(String value) { String t=value.toUpperCase(Locale.ROOT);if(!Set.of("ATP","WTA").contains(t)) throw new IllegalArgumentException();return t; }
    public static void page(int page,int size) { if(page<0 || page>10000 || size<1 || size>100) throw new IllegalArgumentException(); }
    public static void year(Integer year) { if(year!=null && (year<1 || year>9998)) throw new IllegalArgumentException(); }
    public static String surface(String value) { if(value!=null && !Set.of("Hard","Clay","Grass","Carpet").contains(value)) throw new IllegalArgumentException();return value; }
}
