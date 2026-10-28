package com.shuminapp.tennis.player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerController {
    private final PlayerService service;
    public PlayerController(PlayerService service) { this.service=service; }

    @GetMapping("/v1/players")
    public PlayerRepository.SearchPage search(@RequestParam(defaultValue="") String q,
            @RequestParam(required=false) String tour, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.search(q,tour,page,size);
    }
}
