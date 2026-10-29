package com.shuminapp.tennis.features;

import org.springframework.web.bind.annotation.*;

@RestController
public class H2HController {
    private final H2HService service;
    public H2HController(H2HService service) { this.service=service; }
    @GetMapping("/v1/h2h") public H2HRepository.Result h2h(@RequestParam String tour,@RequestParam int first,@RequestParam int second,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) String surface) { return service.between(tour,first,second,from,to,surface); }
}
