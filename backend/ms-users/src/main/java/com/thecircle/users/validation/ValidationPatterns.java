package com.thecircle.users.validation;

/**
 * Central place for the regexes used by the bean-validation annotations across
 * the user-facing DTOs. Keeping them here (as compile-time constants, so
 * {@code @Pattern(regexp = ...)} can reference them) means the same
 * name/password/id rules are applied identically on every endpoint.
 *
 * <p>The human-readable failure messages are no longer kept here: the
 * annotations carry an i18n key (e.g. {@code "validation.password.pattern"})
 * that {@code ValidationErrorHandler} resolves in the request locale from
 * {@code i18n/messages*.properties}, so the frontend shows field errors in the
 * user's language.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** Letters of any language (accents included), spaces, dots, hyphens, apostrophes. */
    public static final String NAME = "^[\\p{L} .'\\-]{1,100}$";

    /**
     * 8-72 characters (72 is the byte cap BCrypt silently truncates at, so we
     * refuse longer to avoid surprises and password-length DoS), with at least
     * one lower-case letter, one upper-case letter and one digit. Character set
     * is an explicit allow-list: letters, digits and a small symbol set.
     */
    public static final String PASSWORD =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d@$!%*?&#._+\\-]{8,72}$";

    /** National ID / passport: letters, digits and hyphens only. */
    public static final String ID_NUMBER = "^[A-Za-z0-9\\-]{1,50}$";

    /**
     * Reject tag-like sequences ({@code <} immediately followed by a letter,
     * {@code /} or {@code !}) so HTML/script payloads — {@code <script>},
     * {@code </b>}, {@code <!--} — cannot be stored, while still allowing a bare
     * {@code <} or {@code >} in ordinary prose ({@code "size > 10cm"},
     * {@code "talla < M"}). Output-encoding at render time remains the real XSS
     * defence; this just keeps markup out of the database. {@code (?s)} so the
     * check also covers multi-line text.
     */
    public static final String NO_ANGLE = "(?s)^(?!.*<[a-zA-Z/!]).*$";

    /** Canonical UUID form, used for opaque session identifiers. */
    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    /** Six-digit one-time code (matches signature.otp.length default). */
    public static final String OTP = "^\\d{6}$";
}
