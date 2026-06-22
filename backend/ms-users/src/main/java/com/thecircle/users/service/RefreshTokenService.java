package com.thecircle.users.service;

import com.thecircle.users.model.RefreshToken;
import com.thecircle.users.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues, rotates and revokes refresh tokens backing the access-token refresh
 * flow (issue #89). The access JWT stays short-lived (1h); this lets a client
 * mint a fresh one without re-authenticating, so the user is not forced to log
 * in every hour while a leaked access token still expires quickly.
 *
 * <p>The raw token is a 256-bit URL-safe random string handed to the client in
 * an httpOnly cookie. Only its SHA-256 hash is persisted, so reading the
 * database does not reveal a usable token. Tokens rotate on every refresh: the
 * presented row is deleted and a new one issued, which both limits the lifetime
 * of any single secret and turns token theft into a detectable race (the
 * attacker and the victim cannot both keep refreshing).
 */
@Service
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;

    /** Refresh-token lifetime in ms. Defaults to 30 days. */
    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpiration;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Issues a fresh refresh token for the user and returns the raw value the
     * controller sets as the {@code tc_refresh} cookie. Only the hash is stored.
     */
    @Transactional
    public String issue(Long userId) {
        byte[] rnd = new byte[32];
        RANDOM.nextBytes(rnd);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpiration * 1_000_000))
                .build();
        repository.save(entity);
        return rawToken;
    }

    /**
     * Validates and rotates the presented raw token. On success the old row is
     * deleted, a new token is issued and returned with its owner's id. Returns
     * empty when the token is unknown, already rotated or expired — the caller
     * maps that to a 401 so the client falls back to a full login.
     */
    @Transactional
    public Optional<Rotation> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> match = repository.findByTokenHash(hash(rawToken));
        if (match.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = match.get();
        // Single-use: delete first so a replay of the same value finds nothing.
        repository.delete(token);
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        String newRaw = issue(token.getUserId());
        return Optional.of(new Rotation(token.getUserId(), newRaw));
    }

    /** Revokes a single token (logout on the current device). Idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(repository::delete);
    }

    /** Revokes every refresh token for a user (e.g. after a password reset). */
    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.deleteByUserId(userId);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected error. Please contact our support team.", e);
        }
    }

    /** Result of a successful rotation: the owner and the new raw token. */
    public static final class Rotation {
        public final Long userId;
        public final String rawToken;

        public Rotation(Long userId, String rawToken) {
            this.userId = userId;
            this.rawToken = rawToken;
        }
    }
}
