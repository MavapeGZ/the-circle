package com.thecircle.contracts.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sanitised bean-validation error response for the contract / signature /
 * payment / chat DTOs. Returns {@code {error, fields:[{field, message}]}} and
 * never includes the rejected value, so a failing {@code PaymentRequestDto}
 * field cannot echo the card number / CVC into the HTTP body.
 *
 * <p>The DTO annotations carry an i18n key as their message (e.g.
 * {@code "validation.cardNumber.required"}); it is resolved here in the request
 * locale (from {@code Accept-Language}) so the frontend shows field errors in
 * the user's language.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ValidationErrorHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> toSafeError(fe, locale))
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", localize("validation.invalidRequest", locale));
        body.put("fields", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Map<String, String> toSafeError(FieldError fe, Locale locale) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("field", fe.getField());
        out.put("message", localize(fe.getDefaultMessage(), locale));
        // Deliberately omitted: getRejectedValue(). Echoing it would surface
        // card number / CVC values for any failing payment field.
        return out;
    }

    // Resolves an i18n key; falls back to the raw string when it is not a known
    // key so we never surface an empty error.
    private String localize(String keyOrText, Locale locale) {
        if (keyOrText == null) return null;
        return messageSource.getMessage(keyOrText, null, keyOrText, locale);
    }
}
