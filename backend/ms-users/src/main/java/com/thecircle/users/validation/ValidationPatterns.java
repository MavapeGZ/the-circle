package com.thecircle.users.validation;

/**
 * Central place for the regexes and human-readable messages used by the
 * bean-validation annotations across the user-facing DTOs. Keeping them here
 * (as compile-time constants, so {@code @Pattern(regexp = ...)} can reference
 * them) means the same name/password/id rules are applied identically on every
 * endpoint and the English error copy is written once.
 *
 * <p>All messages are intentionally in English: the API is the security
 * boundary and returns English copy; the frontend localises its own hints.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Letters of any language (accents included), spaces, dots, hyphens, apostrophes. */
    public static final String NAME = "^[\\p{L} .'\\-]{1,100}$";
    public static final String NAME_MSG =
            "must contain only letters, spaces, dots, hyphens and apostrophes (max 100 characters)";

    /**
     * 8-72 characters (72 is the byte cap BCrypt silently truncates at, so we
     * refuse longer to avoid surprises and password-length DoS), with at least
     * one lower-case letter, one upper-case letter and one digit. Character set
     * is an explicit allow-list: letters, digits and a small symbol set.
     */
    public static final String PASSWORD =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d@$!%*?&#._+\\-]{8,72}$";
    public static final String PASSWORD_MSG =
            "must be 8-72 characters and include an upper-case letter, a lower-case letter and a digit; "
                    + "allowed symbols are @ $ ! % * ? & # . _ + -";

    /** National ID / passport: letters, digits and hyphens only. */
    public static final String ID_NUMBER = "^[A-Za-z0-9\\-]{1,50}$";
    public static final String ID_NUMBER_MSG = "must contain only letters, digits and hyphens (max 50 characters)";

    /**
     * Reject the angle brackets that every HTML/script injection payload needs.
     * Output-encoding at render time is still the real XSS defence, but blocking
     * {@code <} and {@code >} on input stops the obvious stored-XSS attempts and
     * keeps junk markup out of the database.
     */
    public static final String NO_ANGLE = "^[^<>]*$";
    public static final String NO_ANGLE_MSG = "must not contain '<' or '>' characters";

    /** Canonical UUID form, used for opaque session identifiers. */
    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    public static final String UUID_MSG = "must be a valid session identifier";

    /** Six-digit one-time code (matches signature.otp.length default). */
    public static final String OTP = "^\\d{6}$";
    public static final String OTP_MSG = "must be a 6-digit numeric code";
}
