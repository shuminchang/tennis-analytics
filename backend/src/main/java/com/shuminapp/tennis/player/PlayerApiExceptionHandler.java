package com.shuminapp.tennis.player;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=PlayerController.class)
public class PlayerApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidInput(Exception exception,HttpServletRequest request) {
        if (isDetails(request)) return ResponseEntity.badRequest().body(
                new ApiError("INVALID_PLAYER_IDENTITY","Check tour and playerId."));
        return ResponseEntity.badRequest().body(new ApiError("INVALID_SEARCH","Check q, tour, page and size."));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> unavailable(DataAccessException exception,HttpServletRequest request) {
        if (isDetails(request)) return ResponseEntity.status(503).body(
                new ApiError("PLAYER_UNAVAILABLE","Player details are temporarily unavailable."));
        return ResponseEntity.status(503).body(new ApiError("SEARCH_UNAVAILABLE","Player search is temporarily unavailable."));
    }
    private boolean isDetails(HttpServletRequest request) {
        return "/v1/players/{tour}/{playerId}".equals(
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    }
    public record ApiError(String code,String message) { }
}
