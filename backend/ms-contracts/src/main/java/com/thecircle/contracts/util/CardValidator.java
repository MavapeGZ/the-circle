package com.thecircle.contracts.util;

import java.time.YearMonth;

/**
 * Pure-function checks for the simulated checkout flow. No PSP, no PAN storage —
 * just structural sanity so the demo refuses obviously bad input. The card
 * number ending in {@code 0000} is the deterministic failure path documented
 * in the contract: useful to demo the failure UI without random retries.
 */
public final class CardValidator {

    private CardValidator() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("[\\s-]", "");
    }

    public static boolean luhn(String raw) {
        String digits = normalize(raw);
        if (digits == null || !digits.matches("\\d{12,19}")) return false;
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleIt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Accepts {@code MM/YY} or {@code MM/YYYY}. Expiry month is inclusive (valid through its last day). */
    public static boolean expiryNotPast(String raw) {
        if (raw == null) return false;
        String[] parts = raw.trim().split("/");
        if (parts.length != 2) return false;
        try {
            int month = Integer.parseInt(parts[0].trim());
            int year = Integer.parseInt(parts[1].trim());
            if (year < 100) year += 2000;
            if (month < 1 || month > 12) return false;
            YearMonth expiry = YearMonth.of(year, month);
            return !expiry.isBefore(YearMonth.now());
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static boolean cvc(String raw) {
        return raw != null && raw.trim().matches("\\d{3,4}");
    }

    public static String last4(String raw) {
        String digits = normalize(raw);
        if (digits == null || digits.length() < 4) return null;
        return digits.substring(digits.length() - 4);
    }

    /** Coarse brand sniff from the IIN prefix. Visa/MC/Amex are enough for receipts. */
    public static String brand(String raw) {
        String digits = normalize(raw);
        if (digits == null || digits.isEmpty()) return "UNKNOWN";
        char first = digits.charAt(0);
        if (first == '4') return "VISA";
        if (first == '5') return "MASTERCARD";
        if (first == '3' && digits.length() > 1 && (digits.charAt(1) == '4' || digits.charAt(1) == '7')) return "AMEX";
        return "OTHER";
    }

    /** The deterministic failure path baked into the demo. */
    public static boolean isDemoFailureCard(String raw) {
        String digits = normalize(raw);
        return digits != null && digits.endsWith("0000");
    }
}
