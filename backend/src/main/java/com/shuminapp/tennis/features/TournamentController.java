package com.shuminapp.tennis.features;

import org.springframework.web.bind.annotation.*;

@RestController
public class TournamentController {
    private final TournamentService service;
    public TournamentController(TournamentService service) { this.service=service; }
    @GetMapping("/v1/tournaments")
    public TournamentRepository.EventPage list(@RequestParam(defaultValue="ATP") String tour,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Integer year,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list(tour,q,year,page,size); }
    @GetMapping("/v1/tournaments/{tour}/{id}")
    public TournamentRepository.EventDetail detail(@PathVariable String tour,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.detail(tour,id,page,size); }
}
