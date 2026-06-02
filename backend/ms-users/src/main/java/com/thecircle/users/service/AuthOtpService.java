package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process OTP store for signup email verification and login second-factor.
 * Single-instance only; move to Redis before scaling ms-users horizontally.
 */
@Service
@Slf4j
public class AuthOtpService {

    public enum Purpose { EMAIL_VERIFICATION, LOGIN }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder OTP_ENCODER = new BCryptPasswordEncoder();

    private final ConcurrentHashMap<String, OtpSession> sessions = new ConcurrentHashMap<>();

    @Value("${signature.otp.length:6}")
    private int otpLength;

    @Value("${signature.otp.ttl-seconds:600}")
    private int ttlSeconds;

    @Value("${signature.otp.max-attempts:5}")
    private int maxAttempts;

    public Issued issue(Long userId, String email, Purpose purpose) {
        String raw = generateOtp();
        String hashed = OTP_ENCODER.encode(raw);
        String sessionId = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(ttlSeconds);
        sessions.put(sessionId, new OtpSession(userId, email, hashed, expiry, purpose));
        log.info("OTP issued for {} (purpose={}, session={})", email, purpose, sessionId);
        return new Issued(sessionId, raw, ttlSeconds);
    }

    public OtpSession consume(String sessionId, String otp, Purpose expectedPurpose) {
        if (sessionId == null || otp == null) return null;
        OtpSession session = sessions.get(sessionId);
        if (session == null) return null;
        if (session.purpose != expectedPurpose) return null;
        if (Instant.now().isAfter(session.expiry)) {
            sessions.remove(sessionId);
            return null;
        }
        session.attempts++;
        if (session.attempts > maxAttempts) {
            sessions.remove(sessionId);
            return null;
        }
        if (!OTP_ENCODER.matches(otp, session.hashedOtp)) {
            return null;
        }
        sessions.remove(sessionId);
        return session;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static final class Issued {
        public final String sessionId;
        public final String rawOtp;
        public final int ttlSeconds;

        public Issued(String sessionId, String rawOtp, int ttlSeconds) {
            this.sessionId = sessionId;
            this.rawOtp = rawOtp;
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static final class OtpSession {
        public final Long userId;
        public final String email;
        final String hashedOtp;
        final Instant expiry;
        final Purpose purpose;
        int attempts;

        OtpSession(Long userId, String email, String hashedOtp, Instant expiry, Purpose purpose) {
            this.userId = userId;
            this.email = email;
            this.hashedOtp = hashedOtp;
            this.expiry = expiry;
            this.purpose = purpose;
        }
    }
}
