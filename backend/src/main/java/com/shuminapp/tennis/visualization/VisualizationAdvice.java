package com.shuminapp.tennis.visualization;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses=VisualizationController.class)
public class VisualizationAdvice {
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message","Check visualization parameters.")); }
    @ExceptionHandler({DataAccessException.class,VisualizationRepository.NotReady.class,org.springframework.transaction.TransactionException.class})
    ResponseEntity<?> unavailable(Exception e) { return ResponseEntity.status(503).body(Map.of("code","UNAVAILABLE","message","Visualization source is temporarily unavailable.")); }
}
