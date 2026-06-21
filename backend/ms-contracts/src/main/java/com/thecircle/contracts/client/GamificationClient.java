package com.thecircle.contracts.client;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.dto.EarnedBadgeDto;
import com.thecircle.contracts.dto.SignerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifies ms-gamification when a contract becomes ACTIVE so the points/badge
 * engine can award the deal. Best-effort: a gamification outage must never block
 * or fail a signature, so any error is swallowed and an empty badge list returned.
 *
 * <p>The awarded user depends on the deal type: a donation rewards the owner
 * (the giver), a rental rewards the receiver (the borrower), and a sale rewards
 * the owner (the seller). Points-threshold badges (First Steps, Helper, Champion)
 * fall out of the accumulated points on the gamification side automatically.
 */
@Component
public class GamificationClient {

    private static final Logger log = LoggerFactory.getLogger(GamificationClient.class);
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String EVENTS_PATH = "/api/gamification/events";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public GamificationClient(RestTemplate restTemplate,
                              @Value("${services.gamification.base-url:http://localhost:8084}") String baseUrl,
                              @Value("${services.gamification.internal.api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.internalApiKey = internalApiKey;
    }

    /** Which party a deal type rewards, and the gamification event it maps to. */
    private record Award(String eventType, SignerRole awardeeRole) {}

    private static Award awardFor(ContractType type) {
        if (type == null) return null;
        return switch (type) {
            case CESSION_PERMANENT -> new Award("ITEM_DONATED", SignerRole.OWNER);
            case RENT, CESSION_TEMPORARY -> new Award("ITEM_RENTED_SOLIDARITY", SignerRole.RECEIVER);
            case SALE -> new Award("CONTRACT_SIGNED", SignerRole.OWNER);
        };
    }

    /**
     * Awards the gamification event for a freshly-activated contract. The event is
     * always sent for the rewarded party (so badges/points accrue regardless of who
     * signed last), but badges are only returned when the rewarded party is the
     * {@code currentSigner} — i.e. the person on the screen right now, who can be
     * toasted. The contract id is the idempotency key, so a retried confirm never
     * double-awards.
     */
    public List<EarnedBadgeDto> awardForActivation(ContractDto contract, SignerRole currentSigner) {
        if (contract == null) return List.of();
        Award award = awardFor(contract.getType());
        if (award == null) return List.of();

        Long awardeeId = parseUserId(award.awardeeRole() == SignerRole.OWNER
                ? contract.getOwnerId() : contract.getReceiverId());
        if (awardeeId == null) {
            log.warn("Skipping gamification award for contract {}: missing {} user id",
                    contract.getContractId(), award.awardeeRole());
            return List.of();
        }

        List<EarnedBadgeDto> earned = postEvent(awardeeId, award.eventType(), contract.getContractId());
        // Only the party in front of the screen can be toasted; the other party
        // discovers the badge on their profile later.
        return award.awardeeRole() == currentSigner ? earned : List.of();
    }

    private List<EarnedBadgeDto> postEvent(Long userId, String eventType, String referenceId) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("eventType", eventType);
        body.put("referenceId", referenceId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set(INTERNAL_KEY_HEADER, internalApiKey);
        }

        try {
            AwardResponse response = restTemplate.postForObject(
                    baseUrl + EVENTS_PATH, new HttpEntity<>(body, headers), AwardResponse.class);
            if (response == null || response.newBadges == null) return List.of();
            return response.newBadges.stream()
                    .map(b -> new EarnedBadgeDto(b.code, b.name, b.iconUrl))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Could not award gamification event {} to user {} (contract {}): {}",
                    eventType, userId, referenceId, ex.getMessage());
            return List.of();
        }
    }

    private static Long parseUserId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Subset of ms-gamification's award response we care about. */
    private static class AwardResponse {
        public List<Badge> newBadges;
    }

    private static class Badge {
        public String code;
        public String name;
        public String iconUrl;
    }
}
