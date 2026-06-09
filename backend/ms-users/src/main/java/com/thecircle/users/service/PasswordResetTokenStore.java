package com.thecircle.users.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
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
 *
 * <p>Tokens are bound to the source IP at issue time and the binding is
 * re-checked on consume. If the IP differs (token leaked to a different
 * machine, browser extension exfiltrated it, etc.) the consume fails. Set
 * {@code auth.password-reset.bind-token-to-ip=false} to relax this binding
 * for environments where mobile users routinely change network between steps.
 */
@Service
@Slf4j
public class PasswordResetTokenStore {

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    @Value("${auth.password-reset.token-ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${auth.password-reset.bind-token-to-ip:true}")
    private boolean bindTokenToIp;

    @PostConstruct
    void validateConfig() {
        if (ttlSeconds <= 0) {
            log.warn("auth.password-reset.token-ttl-seconds={} is non-positive — clamping to 300s.", ttlSeconds);
            ttlSeconds = 300;
        }
    }

    public String issue(Long userId, String sourceIp) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new Entry(userId, sourceIp, Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    /**
     * Returns the userId tied to the token and removes it (single-use). Returns
     * {@code null} if the token is unknown, expired, or — when IP binding is
     * enabled — was issued for a different source IP.
     */
    public Long consume(String token, String sourceIp) {
        if (token == null || token.isBlank()) return null;
        Entry entry = tokens.remove(token);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.expiresAt)) {
            return null;
        }
        if (bindTokenToIp && !Objects.equals(entry.sourceIp, sourceIp)) {
            log.warn("Password reset token used from a different IP than issued; userId={} expected={} got={}",
                    entry.userId, entry.sourceIp, sourceIp);
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
        final String sourceIp;
        final Instant expiresAt;

        Entry(Long userId, String sourceIp, Instant expiresAt) {
            this.userId = userId;
            this.sourceIp = sourceIp;
            this.expiresAt = expiresAt;
        }
    }
}
