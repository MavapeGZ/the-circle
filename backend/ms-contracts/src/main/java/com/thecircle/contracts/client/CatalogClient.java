package com.thecircle.contracts.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin client over ms-catalog used to drive article availability as a contract
 * progresses (RESERVED on creation, SOLD once fully signed). Calls the internal
 * (non gateway-routed) endpoint. Failures are swallowed so a catalog outage never
 * blocks contract creation or signing.
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public CatalogClient(RestTemplate restTemplate,
                         @Value("${services.catalog.base-url:http://localhost:8082}") String baseUrl,
                         @Value("${services.catalog.internal.api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    /** Projection of the catalog article fields ms-contracts actually needs. */
    public record ArticleSnapshot(String id, Long authorId, String productType,
                                  Double price, Double guaranteeAmount) {}

    /**
     * Reads an article from the catalog. Returns {@code null} when the id is
     * blank or the lookup fails, so callers can decide whether absence is fatal
     * (contract creation) or recoverable.
     */
    public ArticleSnapshot getArticle(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set(INTERNAL_KEY_HEADER, internalApiKey);
            }
            return restTemplate.exchange(baseUrl + "/internal/catalog/articles/{id}", HttpMethod.GET,
                    new HttpEntity<>(headers), ArticleSnapshot.class, itemId).getBody();
        } catch (RuntimeException ex) {
            log.warn("Could not fetch article {} from ms-catalog: {}", itemId, ex.getMessage());
            return null;
        }
    }

    /**
     * Sets an article's availability. Best-effort: logs and swallows failures.
     * Returns {@code true} on success so callers can escalate logging for critical
     * transitions (e.g. SOLD). A blank itemId is treated as a no-op success.
     */
    public boolean setStatus(String itemId, String status) {
        if (itemId == null || itemId.isBlank()) return true;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set(INTERNAL_KEY_HEADER, internalApiKey);
            }
            String url = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/internal/catalog/articles/{id}/status")
                    .queryParam("status", status)
                    .build(itemId)
                    .toString();
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not set article {} status to {} in ms-catalog: {}", itemId, status, ex.getMessage());
            return false;
        }
    }
}
