package com.shuminapp.tennis.visualization;

import java.util.*;
import org.springframework.util.MultiValueMap;

public record ResearchRequest(String tour,int fromYear,int toYear,String surface,int binWidth,String group,int limit,String x,String y) {
    public static ResearchRequest parse(String kind,MultiValueMap<String,String> p) {
        Set<String> allowed=new HashSet<>(Set.of("tour","fromYear","toYear","surface"));
        if(kind.equals("age")) allowed.addAll(Set.of("binWidth","group"));
        if(kind.equals("profiles") || kind.equals("correlations")) allowed.addAll(Set.of("limit","x","y"));
        if(kind.equals("info")) allowed=Set.of("tour");
        for(var entry:p.entrySet()) if(!allowed.contains(entry.getKey()) || entry.getValue().size()!=1 || entry.getValue().getFirst().isBlank()) throw new IllegalArgumentException();
        String tour=p.getFirst("tour");if(!Set.of("ATP","WTA").contains(tour==null?"":tour)) throw new IllegalArgumentException();
        int from=kind.equals("info")?2024:number(p,"fromYear",-1,1800,2100),to=kind.equals("info")?2024:number(p,"toYear",-1,1800,2100);
        if(from>to || to-from>9) throw new IllegalArgumentException();
        String surface=p.getFirst("surface");if(surface==null) surface="ALL";
        if(!Set.of("ALL","Hard","Clay","Grass","Carpet","Unknown").contains(surface)) throw new IllegalArgumentException();
        int width=number(p,"binWidth",1,1,5);if(width!=1 && width!=5) throw new IllegalArgumentException();
        String group=p.getFirst("group");if(group==null) group="overall";if(!Set.of("overall","surface").contains(group)) throw new IllegalArgumentException();
        return new ResearchRequest(tour,from,to,surface,width,group,number(p,"limit",500,1,1000),p.getFirst("x")==null?"height_cm":p.getFirst("x"),p.getFirst("y")==null?"ace_rate":p.getFirst("y"));
    }
    private static int number(MultiValueMap<String,String> p,String key,int fallback,int min,int max) {
        String s=p.getFirst(key);if(s==null) { if(fallback<0) throw new IllegalArgumentException();return fallback; }
        if(!s.matches("[0-9]{1,4}")) throw new IllegalArgumentException();int n=Integer.parseInt(s);if(n<min || n>max) throw new IllegalArgumentException();return n;
    }
}
