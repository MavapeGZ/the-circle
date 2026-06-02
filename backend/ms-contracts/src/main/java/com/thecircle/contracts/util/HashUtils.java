package com.thecircle.contracts.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {

    private HashUtils() {}

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available in JVM", ex);
        }
    }
}
