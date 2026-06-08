package com.thecircle.catalog.controllers;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleStatus;
import com.thecircle.catalog.service.ArticleService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/**
 * Service-to-service endpoint for ms-contracts to drive article availability as
 * contracts progress (RESERVED on creation, SOLD once fully signed). Lives under
 * /internal/** which the API gateway does NOT route, and is guarded by a shared
 * internal API key.
 */
@RestController
@RequestMapping("/internal/catalog/articles")
@RequiredArgsConstructor
public class InternalArticleController {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final Logger log = LoggerFactory.getLogger(InternalArticleController.class);

    private final ArticleService service;
    private final Environment environment;

    @Value("${catalog.internal.api-key:}")
    private String internalApiKey;

    /**
     * Fail closed outside local/dev: an empty key in a shared/staging/prod environment
     * would let anyone flip article availability. Refuse to start there; in local/dev a
     * blank key only logs a loud warning (guard disabled).
     */
    @PostConstruct
    void verifyKeyConfigured() {
        if (internalApiKey != null && !internalApiKey.isBlank()) return;
        if (isDevLikeProfile()) {
            log.warn("catalog.internal.api-key is blank: /internal/catalog guard is DISABLED. "
                    + "Acceptable for local dev only; set CATALOG_INTERNAL_KEY in shared environments.");
            return;
        }
        throw new IllegalStateException(
                "catalog.internal.api-key must be set outside local/dev (set CATALOG_INTERNAL_KEY); "
                        + "/internal/catalog must not run unguarded.");
    }

    private boolean isDevLikeProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) return true; // no profile set → treat as local dev
        return Arrays.stream(active).anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Article> updateStatus(@PathVariable String id,
                                                @RequestParam ArticleStatus status,
                                                HttpServletRequest request) {
        assertInternalCaller(request);
        return ResponseEntity.ok(service.updateStatus(id, status));
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
}
