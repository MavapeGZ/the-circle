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
     * Reject the angle brackets every HTML/script injection payload needs.
     * Output-encoding at render time is still the real XSS defence, but this
     * keeps markup like {@code <strong>} / {@code <script>} out of stored titles
     * and descriptions.
     */
    public static final String NO_ANGLE = "^[^<>]*$";
    public static final String NO_ANGLE_MSG = "must not contain '<' or '>' characters";
}
