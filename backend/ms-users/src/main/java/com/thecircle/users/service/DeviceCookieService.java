package com.thecircle.users.service;

import com.thecircle.users.model.KnownDevice;
import com.thecircle.users.repository.KnownDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and validates device-trust cookies for login OTP gating.
 * Token format: {randomId}.{hmacSha256(randomId|userId)}. Persisted in known_devices.
 */
@Service
@Slf4j
public class DeviceCookieService {

    public static final String COOKIE_NAME = "tc_device";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KnownDeviceRepository repository;

    @Value("${auth.device.cookie.secret:${jwt.secret}}")
    private String secret;

    public DeviceCookieService(KnownDeviceRepository repository) {
        this.repository = repository;
    }

    public boolean isKnownDevice(Long userId, String rawCookie) {
        if (rawCookie == null || rawCookie.isBlank() || userId == null) return false;
        int sep = rawCookie.indexOf('.');
        if (sep <= 0 || sep >= rawCookie.length() - 1) return false;
        String randomId = rawCookie.substring(0, sep);
        String sig = rawCookie.substring(sep + 1);
        if (!constantTimeEquals(sig, hmac(randomId + "|" + userId))) return false;

        Optional<KnownDevice> match = repository.findByUserIdAndDeviceToken(userId, rawCookie);
        if (match.isEmpty()) return false;
        KnownDevice device = match.get();
        device.setLastSeenAt(LocalDateTime.now());
        repository.save(device);
        return true;
    }

    public String issueDeviceCookie(Long userId, String userAgent) {
        byte[] rnd = new byte[24];
        RANDOM.nextBytes(rnd);
        String randomId = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
        String sig = hmac(randomId + "|" + userId);
        String token = randomId + "." + sig;

        KnownDevice device = KnownDevice.builder()
                .userId(userId)
                .deviceToken(token)
                .userAgent(truncate(userAgent, 255))
                .build();
        repository.save(device);
        return token;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected error. Please contact our support team.", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
