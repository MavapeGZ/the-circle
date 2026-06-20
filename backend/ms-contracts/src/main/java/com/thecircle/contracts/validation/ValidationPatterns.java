package com.thecircle.contracts.validation;

/**
 * Shared regexes + English messages for the bean-validation annotations on the
 * contract / signature / payment DTOs. Compile-time constants so they can be
 * referenced from {@code @Pattern(regexp = ...)}.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /**
     * Reject the angle brackets every HTML/script injection payload needs.
     * Output-encoding at render time remains the real XSS defence; this keeps
     * obvious markup out of stored free-text fields.
     */
    public static final String NO_ANGLE = "^[^<>]*$";
    public static final String NO_ANGLE_MSG = "must not contain '<' or '>' characters";

    /** Opaque identifiers (item / owner / receiver / contract): no markup, bounded. */
    public static final String ID = "^[A-Za-z0-9._\\-]{1,64}$";
    public static final String ID_MSG = "must contain only letters, digits, dots, underscores and hyphens (max 64 characters)";

    /** Canonical UUID, used for OTP signing-session identifiers. */
    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    public static final String UUID_MSG = "must be a valid session identifier";

    /** Six-digit one-time code (matches signature.otp.length default). */
    public static final String OTP = "^\\d{6}$";
    public static final String OTP_MSG = "must be a 6-digit numeric code";

    /**
     * Upper bound on a base64-encoded signature image. ~2.8M chars of base64
     * decode to ~2 MB of image, which is plenty for a drawn signature and caps
     * the JSON payload so a huge string cannot exhaust memory.
     */
    public static final int MAX_SIGNATURE_IMAGE_CHARS = 2_800_000;
}
