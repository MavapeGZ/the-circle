package com.thecircle.notifications.controller;

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
 * Sanitised bean-validation error response.
 *
 * <p>{@link MethodArgumentNotValidException} is thrown by Spring when an
 * {@code @Valid @RequestBody} payload fails validation. The default response
 * echoes the rejected value of every failed field, which here is dangerous:
 * {@code EmailRequestDto.variables} carries the OTP code on register/login
 * flows, so a failure on a sibling field (e.g. {@code to} or {@code subject})
 * would serialise the full variables map — including the OTP — into the HTTP
 * response and from there into proxy/SIEM logs and browser dev tools.
 *
 * <p>This handler returns {@code {error, fields:[{field, message}]}} and
 * never includes the rejected value.
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
        // Deliberately omitted: getRejectedValue(). The variables map can carry
        // the OTP code, so echoing it would leak credentials.
        return out;
    }
}
