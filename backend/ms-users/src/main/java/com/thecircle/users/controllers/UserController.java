package com.thecircle.users.controllers;

import com.thecircle.users.dto.ReviewDto;
import com.thecircle.users.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/health")
    public String health() {
        return "ms-users OK";
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserDto> register() {
        // Mock Implementation
        return ResponseEntity.ok(new UserDto("u1", "Mock User", "Madrid", 0.0));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<String> login() {
        return ResponseEntity.ok("mock-jwt-token");
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUserProfile(@PathVariable String userId) {
        return ResponseEntity.ok(new UserDto(userId, "Mock User " + userId, "Barcelona", 4.5));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> updateUserProfile(@PathVariable String userId, @RequestBody UserDto dto) {
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{userId}/reviews")
    public ResponseEntity<ReviewDto> createReview(@PathVariable String userId, @RequestBody ReviewDto dto) {
        return ResponseEntity.ok(new ReviewDto("r1", dto.reviewerId(), userId, dto.contractId(), dto.rating(), dto.comment(), LocalDateTime.now()));
    }

    @GetMapping("/{userId}/reviews")
    public ResponseEntity<List<ReviewDto>> getUserReviews(@PathVariable String userId) {
        return ResponseEntity.ok(List.of(
            new ReviewDto("r1", "u2", userId, "c1", 5, "Great user", LocalDateTime.now())
        ));
    }
}
