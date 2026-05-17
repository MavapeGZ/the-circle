package com.thecircle.gamification.controller;

import com.thecircle.gamification.dto.*;
import com.thecircle.gamification.service.GamificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-gamification OK");
    }

    @PostMapping("/events")
    public ResponseEntity<AwardEventResponseDto> awardEvent(@RequestBody @Valid AwardEventDto dto) {
        return ResponseEntity.ok(gamificationService.processEvent(dto));
    }

    @GetMapping("/users/{userId}/summary")
    public ResponseEntity<UserSummaryDto> getUserSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(gamificationService.getUserSummary(userId));
    }

    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<List<BadgeDto>> getUserBadges(@PathVariable Long userId) {
        return ResponseEntity.ok(gamificationService.getUserBadges(userId));
    }

    @GetMapping("/badges")
    public ResponseEntity<List<BadgeDto>> getAllBadges() {
        return ResponseEntity.ok(gamificationService.getAllBadges());
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard() {
        return ResponseEntity.ok(gamificationService.getLeaderboard());
    }
}
