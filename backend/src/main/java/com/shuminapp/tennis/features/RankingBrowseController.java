package com.shuminapp.tennis.features;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class RankingBrowseController {
    private final RankingBrowseService service;
    public RankingBrowseController(RankingBrowseService service) { this.service=service; }
    @GetMapping("/v1/rankings") public RankingBrowseRepository.Board board(@RequestParam(defaultValue="ATP") String tour,@RequestParam(required=false) String date,@RequestParam(defaultValue="100") int size) { return service.board(tour,date,size); }
    @GetMapping("/v1/players/{tour}/{id}/ranking-chart") public RankingBrowseRepository.Chart chart(@PathVariable String tour,@PathVariable int id) { return service.chart(tour,id); }
    @GetMapping("/v1/home") public List<Map<String,Object>> home() { return service.imports(); }
}
