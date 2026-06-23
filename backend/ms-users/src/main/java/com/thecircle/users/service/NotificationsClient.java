package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin HTTP client for ms-notifications email endpoint.
 */
@Service
@Slf4j
public class NotificationsClient {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;

    @Value("${notifications.base-url:http://localhost:8085}")
    private String baseUrl;

    @Value("${notifications.internal.api-key:}")
    private String apiKey;

    public NotificationsClient(RestTemplate notificationsRestTemplate) {
        this.restTemplate = notificationsRestTemplate;
    }

    /**
     * Sends a templated email. The subject is resolved by ms-notifications from
     * {@code subjectKey} against the recipient's {@code locale} (ISO 639-1, e.g.
     * "es"/"en"); a null/blank locale falls back to the English bundle.
     */
    public void sendEmail(String to, String subjectKey, String templateName, String locale, Map<String, Object> variables) {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        body.put("subjectKey", subjectKey);
        body.put("templateName", templateName);
        body.put("locale", locale);
        body.put("variables", variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(INTERNAL_KEY_HEADER, apiKey);
        }

        try {
            restTemplate.postForEntity(baseUrl + "/api/notifications/email",
                    new HttpEntity<>(body, headers), Void.class);
        } catch (RestClientException e) {
            log.error("Failed to call ms-notifications for {} (template={})", to, templateName, e);
            throw new NotificationsClient.DeliveryException("Failed to deliver email", e);
        }
    }

    public static class DeliveryException extends RuntimeException {
        public DeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
