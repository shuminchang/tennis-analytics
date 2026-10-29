package com.shuminapp.tennis.player;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MatchController {
    private final MatchService service;
    public MatchController(MatchService service) { this.service=service; }
    @GetMapping("/v1/players/{tour}/{playerId}/matches")
    public ResponseEntity<?> history(@PathVariable String tour,@PathVariable int playerId,@RequestParam(required=false) Integer year,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var history=service.history(tour,playerId,year,page,size);
        if(history.isEmpty()) return ResponseEntity.status(404).body(new PlayerApiExceptionHandler.ApiError("PLAYER_NOT_FOUND","Player not found."));
        return ResponseEntity.ok(history.get());
    }
}
