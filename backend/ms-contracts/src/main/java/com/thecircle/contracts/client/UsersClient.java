package com.thecircle.contracts.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin read-only client over ms-users. Used to enrich contracts with signer
 * identity (names) before rendering the PDF. Failures are swallowed by callers
 * so a users outage never blocks signing.
 */
@Component
public class UsersClient {

    private static final Logger log = LoggerFactory.getLogger(UsersClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UsersClient(RestTemplate restTemplate,
                       @Value("${services.users.base-url:http://localhost:8081}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** Minimal projection of ms-users' UserProfileDto. */
    public record UserProfile(Long id, String email, String firstName, String lastName, String kycStatus) {
        public String fullName() {
            String first = firstName != null ? firstName.trim() : "";
            String last = lastName != null ? lastName.trim() : "";
            String name = (first + " " + last).trim();
            return name.isEmpty() ? null : name;
        }
    }

    /** Returns the user profile, or {@code null} if the id is blank or the lookup fails. */
    public UserProfile getProfile(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            return restTemplate.getForObject(baseUrl + "/api/users/{id}", UserProfile.class, userId);
        } catch (RuntimeException ex) {
            log.warn("Could not fetch user {} from ms-users: {}", userId, ex.getMessage());
            return null;
        }
    }
}
