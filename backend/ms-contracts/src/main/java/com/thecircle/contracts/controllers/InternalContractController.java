package com.thecircle.contracts.controllers;

import com.thecircle.contracts.service.ContractService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Internal maintenance endpoint used by ms-users to remove the deleted user's
 * open owner-side contracts and their related artifacts.
 */
@RestController
@RequestMapping("/internal/contracts")
@RequiredArgsConstructor
public class InternalContractController {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final Logger log = LoggerFactory.getLogger(InternalContractController.class);

    private final ContractService contractService;
    private final Environment environment;

    @Value("${contracts.internal.api-key:}")
    private String internalApiKey;

    @PostConstruct
    void verifyKeyConfigured() {
        if (internalApiKey != null && !internalApiKey.isBlank()) return;
        if (isDevLikeProfile()) {
            log.warn("contracts.internal.api-key is blank: /internal/contracts guard is DISABLED. "
                    + "Acceptable for local dev only; set CONTRACTS_INTERNAL_KEY in shared environments.");
            return;
        }
        throw new IllegalStateException(
                "contracts.internal.api-key must be set outside local/dev (set CONTRACTS_INTERNAL_KEY); "
                        + "/internal/contracts must not run unguarded.");
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteOpenOwnerContracts(@PathVariable String userId, HttpServletRequest request) {
        assertInternalCaller(request);
        contractService.deleteOpenOwnerContracts(userId);
        return ResponseEntity.ok().build();
    }

    private void assertInternalCaller(HttpServletRequest request) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            return;
        }
        if (!internalApiKey.equals(request.getHeader(INTERNAL_KEY_HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to perform this action.");
        }
    }

    private boolean isDevLikeProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) return true;
        return Arrays.stream(active).anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
    }
}