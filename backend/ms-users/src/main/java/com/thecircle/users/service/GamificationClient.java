package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class GamificationClient {

    private final RestTemplate restTemplate;

    @Value("${gamification.base-url:http://localhost:8084}")
    private String baseUrl;

    public GamificationClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public record BadgeSummary(Long id, String code, String name, String description,
                               String iconUrl, String tier, LocalDateTime earnedAt) {}

    public record UserSummary(Long userId, int totalPoints, List<BadgeSummary> badges) {}

    public List<UserSummary> getSummaries(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try {
            String ids = String.join(",", userIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .map(String::valueOf)
                    .toList());
            if (ids.isBlank()) {
                return List.of();
            }

            var response = restTemplate.exchange(
                    baseUrl + "/api/gamification/users/summaries?ids={ids}",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UserSummary>>() {},
                    ids);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RuntimeException ex) {
            log.warn("Could not fetch gamification summaries for users {}: {}", userIds, ex.getMessage());
            return List.of();
        }
    }
}
