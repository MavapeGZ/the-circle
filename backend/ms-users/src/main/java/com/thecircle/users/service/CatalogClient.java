package com.thecircle.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogClient {

    private final RestTemplate restTemplate;

    @Value("${catalog.base-url}")
    private String catalogBaseUrl;

    @Value("${catalog.internal.api-key}")
    private String internalApiKey;

    public void removeUserArticles(Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set("X-Internal-Api-Key", internalApiKey);
            }

            HttpEntity<Void> request = new HttpEntity<>(headers);
            String endpoint = catalogBaseUrl + "/internal/catalog/articles/users/" + userId;

            restTemplate.exchange(
                    endpoint,
                    HttpMethod.DELETE,
                    request,
                    Void.class
            );
            log.info("Successfully requested article removal for user {} in ms-catalog", userId);
        } catch (Exception e) {
            log.error("Failed to communicate with ms-catalog for user {} article removal", userId, e);
        }
    }
}
