package com.thecircle.users.service;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption helper for at-rest IBAN storage. The key is derived from
 * {@code jwt.secret} via HKDF (RFC 5869) using SHA-256 and a fixed,
 * domain-separated info string so the IBAN key cannot collide with any other
 * use of the same JWT secret elsewhere in the app.
 *
 * <p>The output format is {@code base64(iv || ciphertext+tag)}, which keeps
 * each ciphertext self-contained and lets the algorithm rotate the IV without
 * a separate schema column.
 */
@Component
public class IbanCipher {

    private static final String INFO = "the-circle:iban-enc:v1";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom rng = new SecureRandom();
    private byte[] aesKey;

    @Value("${jwt.secret}")
    private String jwtSecretBase64;

    @PostConstruct
    void deriveKey() {
        byte[] ikm;
        try {
            ikm = Decoders.BASE64.decode(jwtSecretBase64);
        } catch (RuntimeException ex) {
            // The JWT secret is allowed to be non-base64 in some configs; fall back to raw bytes.
            ikm = jwtSecretBase64.getBytes(StandardCharsets.UTF_8);
        }
        // HKDF (RFC 5869): salt is empty so extract reduces to HMAC(0, IKM). One
        // 32-byte expansion is enough for AES-256.
        byte[] zeroSalt = new byte[32];
        byte[] prk = hmacSha256(zeroSalt, ikm);
        byte[] info = INFO.getBytes(StandardCharsets.UTF_8);
        byte[] t = new byte[info.length + 1];
        System.arraycopy(info, 0, t, 0, info.length);
        t[info.length] = 0x01;
        aesKey = hmacSha256(prk, t);
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HKDF: HMAC-SHA256 not available", ex);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            rng.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("IBAN encryption failed", ex);
        }
    }

    public String decrypt(String ciphertextB64) {
        if (ciphertextB64 == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(ciphertextB64);
            if (in.length < IV_LEN + 1) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[in.length - IV_LEN];
            System.arraycopy(in, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("IBAN decryption failed", ex);
        }
    }
}
