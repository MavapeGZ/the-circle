package com.thecircle.users.controllers;

import com.thecircle.users.dto.UserIdentityDto;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Service-to-service identity endpoint. Returns PII (address, ID number) needed
 * to render signed contract PDFs. Lives under /internal/** which the API gateway
 * does NOT route, so it is not reachable from the public internet, and is further
 * guarded by a shared internal API key.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final Logger log = LoggerFactory.getLogger(InternalUserController.class);

    private final UserRepository userRepository;
    private final Environment environment;

    @Value("${users.internal.api-key:}")
    private String internalApiKey;

    /**
     * Fail closed outside local/dev: this endpoint returns PII, so an empty key in a
     * shared/staging/prod environment would leave it wide open. We refuse to start
     * there; in local/dev a blank key only logs a loud warning (guard disabled).
     */
    @PostConstruct
    void verifyKeyConfigured() {
        if (internalApiKey != null && !internalApiKey.isBlank()) return;
        if (isDevLikeProfile()) {
            log.warn("users.internal.api-key is blank: /internal/users identity guard is DISABLED. "
                    + "Acceptable for local dev only; set USERS_INTERNAL_KEY in shared environments.");
            return;
        }
        throw new IllegalStateException(
                "users.internal.api-key must be set outside local/dev (set USERS_INTERNAL_KEY); "
                        + "/internal/users exposes PII and must not run unguarded.");
    }

    private boolean isDevLikeProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) return true; // no profile set → treat as local dev
        return Arrays.stream(active).anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
    }

    @GetMapping("/{userId}/identity")
    public ResponseEntity<UserIdentityDto> identity(@PathVariable Long userId, HttpServletRequest request) {
        assertInternalCaller(request);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(new UserIdentityDto(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getAddress(), u.getIdNumber(),
                u.getZone(), u.getLanguage()));
    }

    /**
     * Service-to-service IBAN presence check. Returns whether the user has a
     * payout IBAN on file plus the masked last 4 digits for receipts. Used by
     * ms-catalog at publish time and ms-contracts at payout release time. The
     * plaintext IBAN never leaves this service.
     */
    @GetMapping("/{userId}/payout-account")
    public ResponseEntity<PayoutAccountResponse> payoutAccount(@PathVariable Long userId, HttpServletRequest request) {
        assertInternalCaller(request);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean hasIban = u.getIbanEncrypted() != null && !u.getIbanEncrypted().isBlank();
        return ResponseEntity.ok(new PayoutAccountResponse(hasIban, u.getIbanLast4(), u.getZone()));
    }

    public record PayoutAccountResponse(boolean hasIban, String ibanLast4, String zone) {}

    private void assertInternalCaller(HttpServletRequest request) {
        // Blank key disables the guard (local dev), mirroring ms-notifications.
        if (internalApiKey == null || internalApiKey.isBlank()) {
            return;
        }
        if (!internalApiKey.equals(request.getHeader(INTERNAL_KEY_HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to perform this action.");
        }
    }
}
