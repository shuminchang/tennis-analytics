package com.shuminapp.tennis.features;

import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {
    private final MemberService service;
    public MemberController(MemberService service) { this.service=service; }
    @GetMapping("/v1/compare") public List<MemberService.Comparison> compare(@RequestParam String tour,@RequestParam String ids,@RequestParam(required=false) Integer year,@RequestParam(required=false) String surface) { return service.compare(tour,ids,year,surface); }
    @GetMapping("/v1/favorites") public List<Map<String,Object>> list(Authentication auth) { return service.favorites(auth.getName()); }
    @PostMapping("/v1/favorites") public void add(Authentication auth,@RequestBody Favorite favorite) { service.add(auth.getName(),favorite.tour(),favorite.playerId()); }
    @DeleteMapping("/v1/favorites/{tour}/{id}") public void remove(Authentication auth,@PathVariable String tour,@PathVariable int id) { service.remove(auth.getName(),tour,id); }
    public record Favorite(String tour,int playerId) { }
}
