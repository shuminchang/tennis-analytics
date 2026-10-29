package com.shuminapp.tennis.features;

import org.springframework.web.bind.annotation.*;

@RestController
public class EloController {
    private final EloService service;private final EloBuildService builder;
    public EloController(EloService service,EloBuildService builder) { this.service=service;this.builder=builder; }
    @GetMapping("/v1/players/{tour}/{id}/elo") public EloRepository.History history(@PathVariable String tour,@PathVariable int id,@RequestParam(defaultValue="OVERALL") String dimension) { return service.history(tour,id,dimension); }
    @PostMapping("/v1/admin/elo/rebuild") public EloBuildService.Result rebuild() { return builder.rebuild(); }
}
