package com.shuminapp.tennis.features;

import org.springframework.web.bind.annotation.*;

@RestController
public class StatsController {
    private final StatsService service;
    public StatsController(StatsService service) { this.service=service; }
    @GetMapping("/v1/players/{tour}/{id}/summary")
    public StatsRepository.Summary summary(@PathVariable String tour,@PathVariable int id) { return service.summary(tour,id); }
}
