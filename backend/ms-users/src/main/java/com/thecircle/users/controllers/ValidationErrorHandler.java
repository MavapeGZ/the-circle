package com.thecircle.users.controllers;

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
 * Sanitised handler for bean-validation failures.
 *
 * <p>Spring's default response for {@link MethodArgumentNotValidException}
 * echoes the rejected value of every failed field. That is convenient during
 * development, but a failure on a {@code password} or {@code newPassword}
 * field would otherwise dump the cleartext value into the HTTP response body
 * (and from there into any log aggregator, browser dev tools tab, or proxy
 * access log). We replace it with a list of {@code (field, reason)} pairs and
 * never include the rejected value.
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
        // Deliberately omitted: getRejectedValue(). The default Spring response
        // echoes it, which would surface password / IBAN / card number values
        // for any field that fails @NotBlank, @Size, @Pattern, etc.
        return out;
    }
}
