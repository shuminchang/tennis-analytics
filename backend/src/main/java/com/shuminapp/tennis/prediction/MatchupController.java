package com.shuminapp.tennis.prediction;

import java.time.LocalDate;
import java.util.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/matchup-prediction")
public class MatchupController {
    private final MatchupService service;
    public MatchupController(MatchupService service){this.service=service;}
    @GetMapping("/metadata") public MatchupService.Metadata metadata(@RequestParam MultiValueMap<String,String> p){validate(p,Set.of("tour"));return service.metadata(p.getFirst("tour"));}
    @GetMapping public MatchupService.Result predict(@RequestParam MultiValueMap<String,String> p){
        validate(p,Set.of("tour","first","second","asOf","surface","bestOf"));String date=p.getFirst("asOf");if(date==null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();
        return service.predict(p.getFirst("tour"),number(p,"first"),number(p,"second"),LocalDate.parse(date),p.getFirst("surface"),number(p,"bestOf"));
    }
    private static int number(MultiValueMap<String,String> p,String key){String s=p.getFirst(key);if(s==null || !s.matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException();return Integer.parseInt(s);}
    private static void validate(MultiValueMap<String,String> p,Set<String> allowed){for(var e:p.entrySet())if(!allowed.contains(e.getKey()) || e.getValue().size()!=1 || e.getValue().getFirst().isBlank())throw new IllegalArgumentException();if(p.getFirst("tour")==null)throw new IllegalArgumentException();}
}
