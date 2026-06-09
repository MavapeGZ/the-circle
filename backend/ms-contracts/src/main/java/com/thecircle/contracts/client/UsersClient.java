package com.thecircle.contracts.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin read-only client over ms-users. Used to enrich contracts with signer
 * identity (name, address, ID number) before rendering the PDF. Failures are
 * swallowed by callers so a users outage never blocks signing.
 *
 * <p>Reads from the service-to-service /internal endpoint (not gateway-routed)
 * because it returns PII the public profile endpoint must not expose.
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

    /** Identity projection from ms-users' internal endpoint. */
    public record UserProfile(Long id, String email, String firstName, String lastName,
                              String address, String idNumber) {
        public String fullName() {
            String first = firstName != null ? firstName.trim() : "";
            String last = lastName != null ? lastName.trim() : "";
            String name = (first + " " + last).trim();
            return name.isEmpty() ? null : name;
        }
    }

    public record PayoutAccount(boolean hasIban, String ibanLast4) {}

    /** Returns the payout IBAN status of the seller, or {@code null} on lookup failure. */
    public PayoutAccount getPayoutAccount(String userId) {
        if (userId == null || userId.isBlank()) return null;
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

    /** Returns the user identity, or {@code null} if the id is blank or the lookup fails. */
    public UserProfile getProfile(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set(INTERNAL_KEY_HEADER, internalApiKey);
            }
            return restTemplate.exchange(baseUrl + "/internal/users/{id}/identity", HttpMethod.GET,
                    new HttpEntity<>(headers), UserProfile.class, userId).getBody();
        } catch (RuntimeException ex) {
            log.warn("Could not fetch user {} from ms-users: {}", userId, ex.getMessage());
            return null;
        }
    }
}
