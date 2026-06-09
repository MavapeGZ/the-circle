package com.thecircle.gamification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sanitised bean-validation error response. Returns
 * {@code {error, fields:[{field, message}]}} and never includes the rejected
 * value. Defensive: the current DTOs (AwardEventDto, etc.) carry no secrets,
 * but consistency across services keeps the convention enforceable.
 */
@RestControllerAdvice
public class ValidationErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toSafeError)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Invalid request");
        body.put("fields", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Map<String, String> toSafeError(FieldError fe) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("field", fe.getField());
        out.put("message", fe.getDefaultMessage());
        return out;
    }
}
