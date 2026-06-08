package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived "OTP was just verified" handle for the two-step password reset.
 *
 * <p>The flow is split so the user enters the OTP first and only sees the
 * new-password form once it has been validated. Once an OTP is consumed it
 * cannot be replayed, so this store provides a single-use opaque token that
 * stands in for "the user already proved ownership of the email a few seconds
 * ago — let them finish the reset". TTL is intentionally short so a leaked
 * token expires almost immediately.
 */
@Service
@Slf4j
public class PasswordResetTokenStore {

    private static final long DEFAULT_TTL_SECONDS = 300;

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    @Value("${auth.password-reset.token-ttl-seconds:300}")
    private long ttlSeconds;

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        tokens.put(token, new Entry(userId, Instant.now().plusSeconds(ttl)));
        return token;
    }

    /**
     * Returns the userId tied to the token and removes it (single-use). Returns
     * {@code null} if the token is unknown or expired.
     */
    public Long consume(String token) {
        if (token == null || token.isBlank()) return null;
        Entry entry = tokens.remove(token);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.expiresAt)) {
            return null;
        }
        return entry.userId;
    }

    @Scheduled(fixedDelayString = "${auth.password-reset.token-cleanup-interval-ms:300000}")
    public void evictExpired() {
        Instant now = Instant.now();
        Iterator<ConcurrentHashMap.Entry<String, Entry>> it = tokens.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            ConcurrentHashMap.Entry<String, Entry> e = it.next();
            if (now.isAfter(e.getValue().expiresAt)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("PasswordResetTokenStore evicted {} expired tokens", removed);
        }
    }

    private static final class Entry {
        final Long userId;
        final Instant expiresAt;

        Entry(Long userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
