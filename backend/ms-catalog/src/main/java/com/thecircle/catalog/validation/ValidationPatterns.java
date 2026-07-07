package com.thecircle.catalog.validation;

/**
 * Shared regexes for the bean-validation annotations on the article-write
 * payloads. Compile-time constants so they can be referenced from
 * {@code @Pattern(regexp = ...)}.
 *
 * <p>Failure messages live in {@code i18n/messages*.properties}: the annotations
 * carry an i18n key that {@code ValidationErrorHandler} resolves per request.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /**
     * Reject tag-like sequences ({@code <} immediately followed by a letter,
     * {@code /} or {@code !}) so markup like {@code <strong>} / {@code <script>}
     * cannot be stored in titles or descriptions, while still allowing a bare
     * {@code <} or {@code >} in ordinary prose ({@code "size > 10cm"}). Output-
     * encoding at render time remains the real XSS defence. {@code (?s)} so the
     * check also covers multi-line text.
     */
    public static final String NO_ANGLE = "(?s)^(?!.*<[a-zA-Z/!]).*$";
}
