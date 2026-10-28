package com.shuminapp.tennis.player;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @GetMapping("/v1/players/{tour}/{playerId}")
    public ResponseEntity<?> details(@PathVariable String tour,@PathVariable int playerId) {
        var player = service.findById(tour,playerId);
        if (player.isEmpty()) {
            return ResponseEntity.status(404).body(new PlayerApiExceptionHandler.ApiError(
                    "PLAYER_NOT_FOUND","Player not found."));
        }
        return ResponseEntity.ok(player.get());
    }
}
