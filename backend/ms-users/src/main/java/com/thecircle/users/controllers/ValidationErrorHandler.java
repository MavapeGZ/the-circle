package com.thecircle.users.controllers;

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
        // The DTO annotations carry an i18n key (e.g. "validation.password.pattern")
        // as their message. Resolve it here in the request locale so the frontend
        // shows the field error in the user's language.
        out.put("message", localize(fe.getDefaultMessage(), locale));
        // Deliberately omitted: getRejectedValue(). The default Spring response
        // echoes it, which would surface password / IBAN / card number values
        // for any field that fails @NotBlank, @Size, @Pattern, etc.
        return out;
    }

    // Resolves an i18n key; if the message is not a known key (no such message),
    // falls back to the raw string so we never surface an empty error.
    private String localize(String keyOrText, Locale locale) {
        if (keyOrText == null) return null;
        return messageSource.getMessage(keyOrText, null, keyOrText, locale);
    }
}
