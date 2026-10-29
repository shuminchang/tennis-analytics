package com.shuminapp.tennis.features;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalysisController {
    private final AnalysisService service;
    public AnalysisController(AnalysisService service) { this.service=service; }
    @GetMapping("/v1/players/{tour}/{id}/analysis")
    public List<AnalysisRepository.Breakdown> breakdown(@PathVariable String tour,@PathVariable int id,@RequestParam(defaultValue="surface") String by) { return service.breakdown(tour,id,by); }
}
