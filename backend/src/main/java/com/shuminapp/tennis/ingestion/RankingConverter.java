package com.shuminapp.tennis.ingestion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Map;

public final class RankingConverter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    public record Converted(LocalDate date,Integer playerId,Integer rank,Integer points,Integer tours,String error) { }
    public Converted convert(String tour,Map<String,String> raw) {
        if (!"ATP".equals(tour) && !"WTA".equals(tour)) throw new IllegalArgumentException("Unsupported tour");
        LocalDate date=null;
        Integer player=null,rank=null,points=null,tours=null;
        String error=null;
        try {
            String text=raw.get("ranking_date");
            if (text==null || !text.matches("[0-9]{8}") || text.startsWith("0000")) throw new IllegalArgumentException();
            date=LocalDate.parse(text,DATE);
        } catch(RuntimeException e) { error="INVALID_DATE"; }
        try { player=integer(raw.get("player"),true,false); } catch(IllegalArgumentException e) { error="INVALID_PLAYER_ID"; }
        try { rank=integer(raw.get("rank"),true,false); } catch(IllegalArgumentException e) { error="INVALID_RANK"; }
        try { points=integer(raw.get("points"),false,true); } catch(IllegalArgumentException e) { error="INVALID_POINTS"; }
        try {
            if (tour.equals("WTA")) tours=integer(raw.get("tours"),false,true);
            else if (raw.get("tours")!=null && !raw.get("tours").isEmpty()) throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) { error="INVALID_TOURS"; }
        return new Converted(date,player,rank,points,tours,error);
    }
    private Integer integer(String value,boolean positive,boolean optional) {
        if (value==null || value.isEmpty()) {
            if(optional) return null;
            throw new IllegalArgumentException();
        }
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
        int n=Integer.parseInt(value);
        if(positive && n==0) throw new IllegalArgumentException();
        return n;
    }
}
