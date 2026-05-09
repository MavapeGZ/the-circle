package com.thecircle.users.controllers;

import com.thecircle.users.dto.KycResponse;
import com.thecircle.users.dto.ReviewDto;
import com.thecircle.users.service.KycService;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.model.User;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final KycService kycService;
    private final UserRepository userRepository;

    @GetMapping("/health")
    public String health() {
        return "ms-users OK";
    }

    @PostMapping("/{userId}/kyc")
    public ResponseEntity<KycResponse> uploadIdentity(
            @PathVariable Long userId,
            @RequestParam("front") MultipartFile front,
            @RequestParam("back") MultipartFile back,
            Authentication authentication
    ) {
        try {
            // Security check: only owner or ADMIN can perform this
            if (!isOwnerOrAdmin(authentication, userId)) {
                return ResponseEntity.status(403).body(new KycResponse(false, "Forbidden", null));
            }

            String newJwt = kycService.processKyc(userId, front, back);
            if (newJwt != null) {
                return ResponseEntity.ok(new KycResponse(true, "User verified", newJwt));
            } else {
                // If provider returned false, we consider it rejected/pending
                return ResponseEntity.ok(new KycResponse(false, "Document received; verification pending or rejected", null));
            }
        } catch (Exception e) {
            log.error("KYC verification failed for user {}", userId, e);
            return ResponseEntity.internalServerError().body(new KycResponse(false, "Verification failed. Please try again later.", null));
        }
    }

    @GetMapping("/{userId}/kyc/status")
    public ResponseEntity<KycResponse> kycStatus(@PathVariable Long userId, Authentication authentication) {
        if (!isOwnerOrAdmin(authentication, userId)) {
            return ResponseEntity.status(403).build();
        }

        Optional<User> maybe = userRepository.findById(userId);
        if (maybe.isEmpty()) return ResponseEntity.notFound().build();
        User user = maybe.get();
        KycStatus st = user.getKycStatus();
        if (st == KycStatus.VERIFIED) {
            return ResponseEntity.ok(new KycResponse(true, "VERIFIED", null));
        } else if (st == KycStatus.PENDING_REVIEW) {
            return ResponseEntity.ok(new KycResponse(false, "PENDING_REVIEW", null));
        } else if (st == KycStatus.REJECTED) {
            return ResponseEntity.ok(new KycResponse(false, "REJECTED", null));
        } else {
            return ResponseEntity.ok(new KycResponse(false, "UNVERIFIED", null));
        }
    }

    private boolean isOwnerOrAdmin(Authentication authentication, Long userId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            username = s;
        }
        if (username == null) return false;

        Optional<User> opt = userRepository.findByEmail(username);
        if (opt.isEmpty()) return false;
        User user = opt.get();
        if (user.getId() != null && user.getId().equals(userId)) return true;

        Collection<? extends GrantedAuthority> auths = authentication.getAuthorities();
        return auths.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
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

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return ResponseEntity.status(401).build();
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            username = s;
        }
        if (username == null) return ResponseEntity.status(401).build();

        Optional<com.thecircle.users.model.User> op = userRepository.findByEmail(username);
        if (op.isEmpty()) return ResponseEntity.status(401).build();
        com.thecircle.users.model.User u = op.get();
        return ResponseEntity.ok(new com.thecircle.users.dto.UserProfileDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getKycStatus().name()));
    }
}
