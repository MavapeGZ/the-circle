package com.thecircle.catalog.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over Spring's {@link MessageSource} that resolves a localized
 * message for the current request locale. The locale is taken from
 * {@link LocaleContextHolder}, which Spring MVC populates from the request's
 * {@code Accept-Language} header (the SPA sends the user's chosen language).
 *
 * <p>User-facing strings surfaced through an exception or a response body go
 * through here so the same key resolves to the caller's language. Keys and
 * translations live in {@code i18n/messages*.properties}.
 */
@Component
@RequiredArgsConstructor
public class Messages {

    private final MessageSource messageSource;

    /** Resolves {@code key} for the current request locale, with optional args. */
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
