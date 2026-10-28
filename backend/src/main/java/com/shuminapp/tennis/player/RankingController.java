package com.shuminapp.tennis.player;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class RankingController {
    private final RankingService service;
    public RankingController(RankingService service) { this.service=service; }
    @GetMapping("/v1/players/{tour}/{playerId}/rankings")
    public ResponseEntity<?> history(@PathVariable String tour,@PathVariable int playerId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var history=service.history(tour,playerId,page,size);
        if(history.isEmpty()) return ResponseEntity.status(404).body(
                new PlayerApiExceptionHandler.ApiError("PLAYER_NOT_FOUND","Player not found."));
        return ResponseEntity.ok(history.get());
    }
}
