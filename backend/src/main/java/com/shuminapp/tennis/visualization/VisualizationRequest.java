package com.shuminapp.tennis.visualization;

import java.time.LocalDate;
import java.util.*;
import org.springframework.util.MultiValueMap;

public record VisualizationRequest(String tour, LocalDate date, int topN, String x, String y, String color, int bins) {
    public static VisualizationRequest parse(String kind, MultiValueMap<String,String> params) {
        Set<String> allowed = switch(kind) {
            case "metadata" -> Set.of("tour");
            case "scatter" -> Set.of("tour","rankingDate","topN","x","y","color");
            case "histogram" -> Set.of("tour","rankingDate","topN","metric","bins");
            default -> throw new IllegalArgumentException();
        };
        params.forEach((key, values) -> { if(!allowed.contains(key) || values.size()!=1 || values.getFirst().isBlank()) throw new IllegalArgumentException(); });
        String tour=params.getFirst("tour");
        if(!Set.of("ATP","WTA").contains(tour==null?"":tour)) throw new IllegalArgumentException();
        String date=params.getFirst("rankingDate");
        if(date!=null && !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        String x=value(params,"x","height_cm"),y=value(params,"y","rank"),color=value(params,"color","hand");
        if(!Set.of("hand","none").contains(color)) throw new IllegalArgumentException();
        if(!Set.of("height_cm:rank","rank:height_cm","height_cm:ranking_points","ranking_points:height_cm").contains(x+":"+y)) throw new IllegalArgumentException();
        if(!value(params,"metric","height_cm").equals("height_cm")) throw new IllegalArgumentException();
        LocalDate parsed=date==null?null:LocalDate.parse(date);if(parsed!=null && parsed.getYear()<1) throw new IllegalArgumentException();
        return new VisualizationRequest(tour,parsed,number(params,"topN",100,1000),x,y,color,number(params,"bins",20,50));
    }
    private static String value(MultiValueMap<String,String> p,String k,String fallback) { return p.containsKey(k)?p.getFirst(k):fallback; }
    private static int number(MultiValueMap<String,String> p,String k,int fallback,int max) {
        String s=p.getFirst(k); if(s==null) return fallback;
        if(!s.matches("[0-9]{1,4}")) throw new IllegalArgumentException();
        int n=Integer.parseInt(s);if(n<1 || n>max) throw new IllegalArgumentException();return n;
    }
}
