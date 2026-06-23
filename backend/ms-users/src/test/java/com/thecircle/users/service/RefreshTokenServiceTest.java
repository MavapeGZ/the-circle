package com.thecircle.users.service;

import com.thecircle.users.model.RefreshToken;
import com.thecircle.users.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Captor
    private ArgumentCaptor<RefreshToken> tokenCaptor;

    private RefreshTokenService service;

    private static final long THIRTY_DAYS_MS = 2_592_000_000L;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository);
        ReflectionTestUtils.setField(service, "refreshExpiration", THIRTY_DAYS_MS);
    }

    @Test
    void issue_persistsHashedTokenAndReturnsRawValue() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(42L);

        assertNotNull(raw);
        verify(repository).save(tokenCaptor.capture());
        RefreshToken saved = tokenCaptor.getValue();
        assertEquals(42L, saved.getUserId());
        // The raw token is never stored — only its 64-char hex SHA-256 hash.
        assertNotEquals(raw, saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
    }

    @Test
    void rotate_validToken_deletesOldAndIssuesNew() {
        String raw = "valid-raw-token";
        RefreshToken existing = RefreshToken.builder()
                .userId(7L)
                .tokenHash(sha256(raw))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(existing));
        when(repository.deleteByTokenHash(sha256(raw))).thenReturn(1);
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RefreshTokenService.Rotation> result = service.rotate(raw);

        assertTrue(result.isPresent());
        assertEquals(7L, result.get().userId);
        assertNotNull(result.get().rawToken);
        assertNotEquals(raw, result.get().rawToken);
        verify(repository).deleteByTokenHash(sha256(raw));
        verify(repository).save(any(RefreshToken.class)); // the new rotated token
    }

    @Test
    void rotate_unknownToken_returnsEmpty() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertTrue(service.rotate("nope").isEmpty());
        verify(repository, never()).deleteByTokenHash(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_lostRace_returnsEmptyAndIssuesNothing() {
        String raw = "raced-token";
        RefreshToken existing = RefreshToken.builder()
                .userId(1L)
                .tokenHash(sha256(raw))
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(existing));
        // Another concurrent request already deleted the row: count comes back 0.
        when(repository.deleteByTokenHash(sha256(raw))).thenReturn(0);

        assertTrue(service.rotate(raw).isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_expiredToken_returnsEmptyAfterDeleting() {
        String raw = "expired-token";
        RefreshToken expired = RefreshToken.builder()
                .userId(1L)
                .tokenHash(sha256(raw))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(expired));
        when(repository.deleteByTokenHash(sha256(raw))).thenReturn(1);

        assertTrue(service.rotate(raw).isEmpty());
        // Row is consumed even though it was expired; no successor is issued.
        verify(repository).deleteByTokenHash(sha256(raw));
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_blankOrNull_returnsEmpty() {
        assertTrue(service.rotate(null).isEmpty());
        assertTrue(service.rotate("  ").isEmpty());
        verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    void revoke_deletesByHash() {
        service.revoke("some-token");
        verify(repository).deleteByTokenHash(sha256("some-token"));
    }

    @Test
    void revoke_blank_isNoop() {
        service.revoke("");
        verify(repository, never()).deleteByTokenHash(anyString());
    }

    @Test
    void revokeAllForUser_delegatesToRepository() {
        service.revokeAllForUser(99L);
        verify(repository).deleteByUserId(99L);
    }

    @Test
    void purgeExpired_callsRepositoryWithNow() {
        when(repository.deleteExpired(any(LocalDateTime.class))).thenReturn(3);
        service.purgeExpired();
        verify(repository, times(1)).deleteExpired(any(LocalDateTime.class));
    }

    @Test
    void issue_producesUniqueTokensAcrossCalls() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        String a = service.issue(1L);
        String b = service.issue(1L);
        assertNotEquals(a, b);
    }

    // Mirrors the production hashing so the test can assert on the stored value.
    private static String sha256(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
