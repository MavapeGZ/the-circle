package com.thecircle.catalog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Base64;
import java.util.Locale;

public class Base64ImageValidator implements ConstraintValidator<Base64Image, String> {

    // ~2.8M base64 chars decode to ~2 MB; caps the JSON payload so a huge image
    // string cannot exhaust memory.
    private static final int MAX_BASE64_CHARS = 2_800_000;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // image is optional
        }

        String data = value;
        String declaredMime = null;

        // Optional data URI prefix: data:image/png;base64,<payload>
        if (data.startsWith("data:")) {
            int comma = data.indexOf(',');
            if (comma < 0) {
                return false;
            }
            String meta = data.substring(5, comma).toLowerCase(Locale.ROOT); // image/png;base64
            if (!meta.endsWith(";base64")) {
                return false;
            }
            declaredMime = meta.substring(0, meta.length() - ";base64".length());
            if (!isAllowedMime(declaredMime)) {
                return false;
            }
            data = data.substring(comma + 1);
        }

        if (data.isEmpty() || data.length() > MAX_BASE64_CHARS) {
            return false;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (bytes.length == 0) {
            return false;
        }

        String actual = detect(bytes);
        if (actual == null) {
            return false; // not a real PNG/JPG
        }
        // If a data-URI MIME was declared, it must agree with the real content.
        if (declaredMime != null && !mimeMatches(declaredMime, actual)) {
            return false;
        }
        return true;
    }

    private static boolean isAllowedMime(String mime) {
        return "image/png".equals(mime) || "image/jpeg".equals(mime) || "image/jpg".equals(mime);
    }

    private static boolean mimeMatches(String mime, String actual) {
        if ("PNG".equals(actual)) {
            return "image/png".equals(mime);
        }
        // JPEG
        return "image/jpeg".equals(mime) || "image/jpg".equals(mime);
    }

    private static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "PNG";
        }
        return null;
    }
}
