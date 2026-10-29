package com.shuminapp.tennis.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

public final class MatchConverter {
    public record Issue(String code,String field,String role,String metric) { }
    public record Converted(Map<String,Object> fields,List<Issue> issues,String error) { }
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final Map<String,String> STATS=new LinkedHashMap<>();
    static {
        STATS.put("ace","ace");STATS.put("df","df");STATS.put("svpt","svpt");STATS.put("1stIn","1st_in");STATS.put("1stWon","1st_won");
        STATS.put("2ndWon","2nd_won");STATS.put("SvGms","sv_gms");STATS.put("bpSaved","bp_saved");STATS.put("bpFaced","bp_faced");
    }
    public Converted convert(String tour,Map<String,String> raw) {
        if(!Set.of("ATP","WTA").contains(tour)) throw new IllegalArgumentException("Unsupported tour");
        Map<String,Object> f=new LinkedHashMap<>();List<Issue> issues=new ArrayList<>();String error=null;
        f.put("tour",tour);
        for(String field:List.of("tourney_id","round")) {
            String value=empty(raw.get(field));f.put(field,value);
            if(value==null || value.isBlank()) error="INVALID_MATCH_KEY";
        }
        for(String field:List.of("match_num","winner_id","loser_id")) {
            try { f.put(field,integer(raw.get(field),Integer.MIN_VALUE,Integer.MAX_VALUE,!field.equals("match_num"))); }
            catch(RuntimeException e) { f.put(field,null);error="INVALID_"+field.toUpperCase(Locale.ROOT); }
        }
        try {
            String date=raw.get("tourney_date");
            if(date==null || !date.matches("[0-9]{8}") || date.startsWith("0000")) throw new IllegalArgumentException();
            f.put("tourney_date",LocalDate.parse(date,DATE).toString());
        } catch(RuntimeException e) { f.put("tourney_date",null);error="INVALID_EVENT_DATE"; }
        for(String field:List.of("tourney_name","surface","tourney_level","score","winner_seed","loser_seed","winner_entry","loser_entry")) f.put(field,empty(raw.get(field)));
        optionalInt(f,raw,issues,"draw_size","draw_size",1,Short.MAX_VALUE,"EVENT_FIELDS","RECORD");
        optionalInt(f,raw,issues,"best_of","best_of",1,Short.MAX_VALUE,"SCORE_ANALYSIS","RECORD");
        if(f.get("best_of")!=null && !Set.of(1,3,5).contains(f.get("best_of"))) { f.put("best_of",null);issues.add(new Issue("INVALID_BEST_OF","best_of","RECORD","SCORE_ANALYSIS")); }
        optionalInt(f,raw,issues,"minutes","minutes",0,Short.MAX_VALUE,"TIME_ANALYSIS","RECORD");
        if(f.get("minutes")!=null && (Set.of(0,1146,987,2475,1507,1094).contains(f.get("minutes")))) issues.add(new Issue("REVIEW_MINUTES","minutes","RECORD","TIME_ANALYSIS"));
        if(f.get("surface")!=null && !Set.of("Hard","Clay","Grass","Carpet").contains(f.get("surface"))) issues.add(new Issue("UNKNOWN_SURFACE","surface","RECORD","SURFACE_ANALYSIS"));
        if(f.get("round")!=null && !Set.of("R128","R64","R32","R16","QF","SF","F","RR","BR","ER").contains(f.get("round"))) issues.add(new Issue("UNKNOWN_ROUND","round","RECORD","STAGE_ANALYSIS"));
        for(String role:List.of("winner","loser")) {
            String upper=role.toUpperCase(Locale.ROOT);
            optionalInt(f,raw,issues,role+"_rank",role+"_rank",1,Integer.MAX_VALUE,"RANK_AT_MATCH",upper);
            optionalInt(f,raw,issues,role+"_rank_points",role+"_rank_points",0,Integer.MAX_VALUE,"RANK_AT_MATCH",upper);
            optionalInt(f,raw,issues,role+"_ht",role+"_height_cm",1,Short.MAX_VALUE,"HEIGHT_ANALYSIS",upper);
            Integer height=(Integer)f.get(role+"_height_cm");
            if(height!=null && (height<100 || height>250)) issues.add(new Issue("SUSPECT_HEIGHT",role+"_ht",upper,"HEIGHT_ANALYSIS"));
            String age=empty(raw.get(role+"_age"));BigDecimal parsed=null;
            if(age!=null) try {
                parsed=new BigDecimal(age).setScale(1,java.math.RoundingMode.UNNECESSARY);
                if(parsed.abs().compareTo(new BigDecimal("999.9"))>0) throw new IllegalArgumentException();
                if(parsed.signum()<=0) issues.add(new Issue("NONPOSITIVE_AGE",role+"_age",upper,"AGE_ANALYSIS"));
            } catch(RuntimeException e) { parsed=null;issues.add(new Issue("INVALID_AGE",role+"_age",upper,"AGE_ANALYSIS")); }
            f.put(role+"_age",parsed);
            if(placeholder(raw.get(role+"_name"))) issues.add(new Issue("PLACEHOLDER_MATCH_NAME",role+"_name",upper,"MATCH_HISTORY"));
            String prefix=role.equals("winner")?"w_":"l_";
            for(var stat:STATS.entrySet()) {
                optionalInt(f,raw,issues,prefix+stat.getKey(),prefix+stat.getValue(),Short.MIN_VALUE,Short.MAX_VALUE,"SERVE_STATS",upper);
                Integer value=(Integer)f.get(prefix+stat.getValue());
                if(value!=null && value<0) issues.add(new Issue("NEGATIVE_STAT",prefix+stat.getKey(),upper,"SERVE_STATS"));
            }
            Integer sv=(Integer)f.get(prefix+"svpt"),first=(Integer)f.get(prefix+"1st_in"),won=(Integer)f.get(prefix+"1st_won"),second=(Integer)f.get(prefix+"2nd_won"),df=(Integer)f.get(prefix+"df"),saved=(Integer)f.get(prefix+"bp_saved"),faced=(Integer)f.get(prefix+"bp_faced");
            if(gt(first,sv) || gt(won,first) || sv!=null && first!=null && (gt(second,sv-first) || gt(df,sv-first)) || won!=null && second!=null && gt(won+second,sv) || gt(saved,faced))
                issues.add(new Issue("INCONSISTENT_SERVE_STATS",prefix+"stats",upper,"SERVE_STATS"));
        }
        if(f.get("winner_id")!=null && f.get("winner_id").equals(f.get("loser_id"))) issues.add(new Issue("SAME_PLAYER","player_id","RECORD","MATCH_HISTORY"));
        String score=(String)f.get("score");
        if(score!=null && (score.equalsIgnoreCase("7-Oct") || score.equals("6-36-3"))) issues.add(new Issue("REVIEW_SCORE","score","RECORD","SCORE_ANALYSIS"));
        return new Converted(Collections.unmodifiableMap(f),List.copyOf(issues),error);
    }
    private boolean gt(Integer a,Integer b) { return a!=null && b!=null && a>b; }
    public static boolean placeholder(String name) {
        return name!=null && name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").matches("(?:u )?unknown(?: unknown)?|x(?: x)?");
    }
    private void optionalInt(Map<String,Object> f,Map<String,String> raw,List<Issue> issues,String source,String target,int min,int max,String metric,String role) {
        Integer value=null;String text=empty(raw.get(source));
        if(text!=null) try { value=integer(text,min,max,false); } catch(RuntimeException e) { issues.add(new Issue("INVALID_NUMBER",source,role,metric)); }
        f.put(target,value);
    }
    private Integer integer(String value,int min,int max,boolean positive) {
        if(value==null || !value.matches("-?[0-9]+")) throw new IllegalArgumentException();
        int n=Integer.parseInt(value);if(n<min || n>max || positive && n<=0) throw new IllegalArgumentException();return n;
    }
    private String empty(String value) { return value==null || value.isEmpty()?null:value; }
}
