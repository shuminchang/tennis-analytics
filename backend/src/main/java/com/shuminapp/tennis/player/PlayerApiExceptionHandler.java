package com.shuminapp.tennis.player;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=PlayerController.class)
public class PlayerApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_SEARCH","Check q, tour, page and size."));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> unavailable(DataAccessException exception) {
        return ResponseEntity.status(503).body(new ApiError("SEARCH_UNAVAILABLE","Player search is temporarily unavailable."));
    }
    public record ApiError(String code,String message) { }
}
