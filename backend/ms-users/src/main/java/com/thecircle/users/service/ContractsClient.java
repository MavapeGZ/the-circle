package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class ContractsClient {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;

    @Value("${contracts.base-url:http://localhost:8083}")
    private String baseUrl;

    @Value("${contracts.internal.api-key:}")
    private String apiKey;

public ContractsClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(2))
                .setReadTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    public void removeOwnedOpenContracts(Long userId) {
        if (userId == null) return;

        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(INTERNAL_KEY_HEADER, apiKey);
        }

        try {
            restTemplate.exchange(baseUrl + "/internal/contracts/users/{userId}", HttpMethod.DELETE,
                    new HttpEntity<>(headers), Void.class, userId);
            log.info("Successfully requested contract cleanup for user {} in ms-contracts", userId);
        } catch (RestClientException e) {
            log.error("Failed to communicate with ms-contracts for user {} contract cleanup", userId, e);
        }
    }
}
