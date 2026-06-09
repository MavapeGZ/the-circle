package com.thecircle.users.service;

import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
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
 *
 * <p>Decryption is intentionally not exposed: the application never needs the
 * plaintext IBAN — payouts and receipts work off {@code iban_last4}. Removing
 * the API removes the only path that would expose the cleartext to an
 * application-layer bug.
 */
@Component
public class IbanCipher {

    private static final Logger log = LoggerFactory.getLogger(IbanCipher.class);
    private static final String INFO = "the-circle:iban-enc:v1";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom rng = new SecureRandom();
    private final Environment environment;
    private byte[] aesKey;

    @Value("${jwt.secret}")
    private String jwtSecretBase64;

    public IbanCipher(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void deriveKey() {
        byte[] ikm;
        try {
            ikm = Decoders.BASE64.decode(jwtSecretBase64);
        } catch (RuntimeException ex) {
            // Fail closed outside local/dev: a non-base64 secret means the key is derived
            // from raw UTF-8 bytes, which produces a different AES key than the base64-decoded
            // path. If the secret is ever rewritten in the "correct" form later, every IBAN
            // encrypted under the raw-bytes key is undecryptable. Refuse to start in shared
            // environments rather than silently quarantining production data.
            if (isDevLikeProfile()) {
                log.warn("jwt.secret is not valid base64 — IbanCipher fell back to raw UTF-8 bytes "
                        + "as HKDF input. Acceptable for local dev only; set a base64 JWT_SECRET in "
                        + "shared environments before any IBAN is encrypted.");
                ikm = jwtSecretBase64.getBytes(StandardCharsets.UTF_8);
            } else {
                throw new IllegalStateException(
                        "jwt.secret must be base64 in non-dev profiles: IbanCipher cannot safely "
                                + "fall back to raw UTF-8 bytes without risking unrecoverable IBANs "
                                + "if the secret is later normalised. Set a base64-encoded JWT_SECRET.",
                        ex);
            }
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

    private boolean isDevLikeProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) return true; // no profile set → treat as local dev
        return Arrays.stream(active).anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
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
}
