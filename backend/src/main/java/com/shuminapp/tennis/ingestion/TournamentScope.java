package com.shuminapp.tennis.ingestion;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.CSVFormat;

public final class TournamentScope {
    public record Decision(String status,String category,String rule,String evidence) { }
    private record ExceptionRule(String level,String status,String category) { }
    private final Map<String,ExceptionRule> exceptions=new HashMap<>();
    public TournamentScope() {
        try(var input=getClass().getResourceAsStream("/ingestion/tournament-exceptions-v1.csv")) {
            if(input==null) throw new IllegalStateException("Missing tournament exceptions");
            try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8);var csv=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                for(var r:csv) if(exceptions.put(r.get("tour")+":"+r.get("tourney_id"),new ExceptionRule(r.get("expected_level"),r.get("status"),r.get("category")))!=null)
                    throw new IllegalStateException("Duplicate tournament exception");
            }
            if(exceptions.size()!=104) throw new IllegalStateException("Expected 104 reviewed exceptions");
        } catch(Exception e) { throw new IllegalStateException("Cannot load scope rules",e); }
    }
    public Decision classify(String tour,String id,String level) {
        var exception=exceptions.get(tour+":"+id);
        if(exception!=null) {
            if(!exception.level().equals(level)) throw new IllegalStateException("Reviewed tournament level changed: "+tour+":"+id);
            return new Decision(exception.status(),exception.category(),"EVENT_EXCEPTION","docs/05-tournament-scope.md; tournament-exceptions-v1.csv");
        }
        String code=level==null?"":level;
        if((tour.equals("ATP")?Set.of("A","G","M","D"):Set.of("G","I","P","PM","T1","T2","T3","T4","T5","D")).contains(code))
            return new Decision("INCLUDE",code.equals("D")?"TEAM_SINGLES":"SOURCE_"+code,"LEVEL_INCLUDE","docs/05-tournament-scope.md; first-version level policy");
        if(Set.of("E","J").contains(code) || tour.equals("WTA") && Set.of("35+H","50+H").contains(code))
            return new Decision("EXCLUDE","SOURCE_"+code,"LEVEL_EXCLUDE","docs/05-tournament-scope.md; first-version exclusions");
        return new Decision("REVIEW",null,"LEVEL_REVIEW","docs/05-tournament-scope.md; unconfirmed classification");
    }
}
