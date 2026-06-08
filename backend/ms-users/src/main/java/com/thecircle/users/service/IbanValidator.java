package com.thecircle.users.service;

import java.math.BigInteger;

/**
 * ISO 13616 IBAN format check (length + mod-97 == 1). Does NOT verify that the
 * account actually exists at a real bank — only that the structure and check
 * digits are well-formed. Sufficient for the symbolic payment simulation.
 */
public final class IbanValidator {

    private IbanValidator() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("\\s+", "").toUpperCase();
    }

    public static boolean isValid(String raw) {
        String iban = normalize(raw);
        if (iban == null || iban.length() < 15 || iban.length() > 34) return false;
        if (!iban.matches("[A-Z0-9]+")) return false;
        // Move the first 4 chars to the end, then map letters A=10..Z=35.
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            if (c >= '0' && c <= '9') {
                numeric.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                numeric.append(c - 'A' + 10);
            } else {
                return false;
            }
        }
        try {
            return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static String last4(String raw) {
        String iban = normalize(raw);
        if (iban == null || iban.length() < 4) return null;
        return iban.substring(iban.length() - 4);
    }
}
