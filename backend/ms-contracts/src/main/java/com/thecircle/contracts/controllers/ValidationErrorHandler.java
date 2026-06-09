package com.thecircle.contracts.controllers;

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
 * <p>None of the current controllers use {@code @Valid}, but
 * {@link com.thecircle.contracts.dto.PaymentRequestDto} carries full PAN and
 * CVC fields. The moment anyone adds bean-validation annotations + {@code @Valid}
 * to that endpoint, Spring's default response would echo the rejected card
 * number into the HTTP body. This advice exists pre-emptively so the drift
 * cannot happen silently — failures return {@code {error, fields:[{field,
 * message}]}} and never include the rejected value.
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
        // Deliberately omitted: getRejectedValue(). Echoing it would surface
        // card number / CVC values for any failing payment field.
        return out;
    }
}
