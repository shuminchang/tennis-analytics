package com.shuminapp.tennis.features;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages="com.shuminapp.tennis.features")
public class FeatureAdvice {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message","Check request parameters.")); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> missing(Exception e) { return ResponseEntity.status(404).body(Map.of("code","NOT_FOUND","message","Record not found.")); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable(Exception e) { return ResponseEntity.status(503).body(Map.of("code","UNAVAILABLE","message","Temporarily unavailable.")); }
}
