package com.shuminapp.tennis.prediction;

import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackageClasses=MatchupController.class)
public class MatchupAdvice {
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST"));}
    @ExceptionHandler(NoSuchElementException.class) ResponseEntity<?> missing(Exception e){return ResponseEntity.status(404).body(Map.of("code","NOT_FOUND"));}
    @ExceptionHandler({MatchupRepository.Unavailable.class,com.shuminapp.tennis.visualization.VisualizationRepository.NotReady.class,DataAccessException.class,org.springframework.transaction.TransactionException.class}) ResponseEntity<?> unavailable(Exception e){return ResponseEntity.status(503).body(Map.of("code","MODEL_UNAVAILABLE","message","Build a model matching the current source and Elo version."));}
}
