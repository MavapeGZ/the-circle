package com.thecircle.gamification.controller;

import com.thecircle.gamification.dto.*;
import com.thecircle.gamification.service.GamificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final GamificationService gamificationService;

    // Shared secret for the internal-only /events endpoint. Blank => guard disabled (dev).
    @Value("${gamification.internal.api-key:}")
    private String internalApiKey;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-gamification OK");
    }

    @PostMapping("/events")
    public ResponseEntity<AwardEventResponseDto> awardEvent(@RequestBody @Valid AwardEventDto dto,
                                                            HttpServletRequest request) {
        assertInternalCaller(request);
        return ResponseEntity.ok(gamificationService.processEvent(dto));
    }

    @GetMapping("/users/{userId}/summary")
    public ResponseEntity<UserSummaryDto> getUserSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(gamificationService.getUserSummary(userId));
    }

    @GetMapping("/users/{userId}/points")
    public ResponseEntity<UserPointsDto> getUserPoints(@PathVariable Long userId) {
        return ResponseEntity.ok(gamificationService.getUserPoints(userId));
    }

    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<List<BadgeDto>> getUserBadges(@PathVariable Long userId) {
        return ResponseEntity.ok(gamificationService.getUserBadges(userId));
    }

    @GetMapping("/users/summaries")
    public ResponseEntity<List<UserSummaryDto>> getUserSummaries(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(gamificationService.getUserSummaries(ids));
    }

    @GetMapping("/badges")
    public ResponseEntity<List<BadgeDto>> getAllBadges() {
        return ResponseEntity.ok(gamificationService.getAllBadges());
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard() {
        return ResponseEntity.ok(gamificationService.getLeaderboard());
    }

    // Awarding points is service-to-service only. When a key is configured, callers
    // must present it; when blank (local dev) the guard is skipped.
    private void assertInternalCaller(HttpServletRequest request) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            return;
        }
        if (!internalApiKey.equals(request.getHeader(INTERNAL_KEY_HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to perform this action. Please contact our support team if you think this is a mistake.");
        }
    }
}
