package com.thecircle.catalog.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Service-to-service read against ms-users used at article publish time to
 * verify the seller has a payout IBAN. A network failure is treated as
 * "unknown" by returning {@code null}, leaving the policy decision to the caller.
 */
@Component
public class UsersClient {

    private static final Logger log = LoggerFactory.getLogger(UsersClient.class);
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public UsersClient(RestTemplate restTemplate,
                       @Value("${services.users.base-url:http://localhost:8081}") String baseUrl,
                       @Value("${services.users.internal.api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    public record PayoutAccount(boolean hasIban, String ibanLast4, String zone) {}

    public PayoutAccount getPayoutAccount(Long userId) {
        if (userId == null) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set(INTERNAL_KEY_HEADER, internalApiKey);
            }
            return restTemplate.exchange(baseUrl + "/internal/users/{id}/payout-account", HttpMethod.GET,
                    new HttpEntity<>(headers), PayoutAccount.class, userId).getBody();
        } catch (RuntimeException ex) {
            log.warn("Could not fetch payout account for user {}: {}", userId, ex.getMessage());
            return null;
        }
    }
}
