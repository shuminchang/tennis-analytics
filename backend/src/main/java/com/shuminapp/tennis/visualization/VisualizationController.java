package com.shuminapp.tennis.visualization;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.shuminapp.tennis.visualization.VisualizationDtos.*;

@RestController
@RequestMapping("/v1/visualizations")
public class VisualizationController {
    private final VisualizationService service;
    public VisualizationController(VisualizationService service) { this.service=service; }
    @GetMapping("/metadata") public Metadata metadata(@RequestParam MultiValueMap<String,String> params) { return service.metadata(VisualizationRequest.parse("metadata",params)); }
    @GetMapping("/scatter") public Scatter scatter(@RequestParam MultiValueMap<String,String> params) { return service.scatter(VisualizationRequest.parse("scatter",params)); }
    @GetMapping("/histogram") public Histogram histogram(@RequestParam MultiValueMap<String,String> params) { return service.histogram(VisualizationRequest.parse("histogram",params)); }
}
