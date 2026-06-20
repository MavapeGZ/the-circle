package com.thecircle.catalog.validation;

/**
 * Shared regexes + English messages for the bean-validation annotations on the
 * article-write payloads. Compile-time constants so they can be referenced from
 * {@code @Pattern(regexp = ...)}.
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
    public static final String NO_ANGLE_MSG = "must not contain HTML tags";
}
