package com.thecircle.catalog.controllers;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleStatus;
import com.thecircle.catalog.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    private final ArticleService service;

    @Value("${catalog.internal.api-key:}")
    private String internalApiKey;

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
