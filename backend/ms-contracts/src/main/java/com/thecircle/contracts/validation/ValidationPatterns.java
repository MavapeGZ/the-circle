package com.thecircle.contracts.validation;

/**
 * Shared regexes for the bean-validation annotations on the contract /
 * signature / payment DTOs. Compile-time constants so they can be referenced
 * from {@code @Pattern(regexp = ...)}.
 *
 * <p>Failure messages live in {@code i18n/messages*.properties}: the annotations
 * carry an i18n key that {@code ValidationErrorHandler} resolves per request.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /**
     * Reject tag-like sequences ({@code <} immediately followed by a letter,
     * {@code /} or {@code !}) so HTML/script payloads cannot be stored, while
     * still allowing a bare {@code <} or {@code >} in ordinary prose. Output-
     * encoding at render time remains the real XSS defence. {@code (?s)} so the
     * check also covers multi-line text.
     */
    public static final String NO_ANGLE = "(?s)^(?!.*<[a-zA-Z/!]).*$";

    /** Opaque identifiers (item / owner / receiver / contract): no markup, bounded. */
    public static final String ID = "^[A-Za-z0-9._\\-]{1,64}$";

    /** Canonical UUID, used for OTP signing-session identifiers. */
    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    /** Six-digit one-time code (matches signature.otp.length default). */
    public static final String OTP = "^\\d{6}$";

    /**
     * Upper bound on a base64-encoded signature image. ~2.8M chars of base64
     * decode to ~2 MB of image, which is plenty for a drawn signature and caps
     * the JSON payload so a huge string cannot exhaust memory.
     */
    public static final int MAX_SIGNATURE_IMAGE_CHARS = 2_800_000;
}
