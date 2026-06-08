package com.thecircle.contracts.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;

/**
 * Stateless JWT verification for the PII-bearing contract PDF endpoints. ms-contracts
 * is not gateway-authenticated, so it verifies the bearer token itself against the
 * shared {@code jwt.secret} (issued by ms-users) and extracts the {@code userId}
 * claim used to authorize access against a contract's owner/receiver.
 */
@Component
public class JwtAuthService {

    private final SecretKey key;

    public JwtAuthService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * Verifies the {@code Authorization: Bearer <jwt>} header and returns the caller's
     * userId. Throws 401 if the header is missing/malformed, the signature is invalid,
     * the token is expired, or the userId claim is absent.
     */
    public String requireUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authHeader.substring(7)).getPayload();
            Object userId = claims.get("userId");
            if (userId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing userId");
            }
            return String.valueOf(userId);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
