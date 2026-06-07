package com.thecircle.users.controllers;

import com.thecircle.users.dto.KycResponse;
import com.thecircle.users.dto.ReviewDto;
import com.thecircle.users.service.KycService;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.model.User;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.dto.UserProfileDto;
import com.thecircle.users.service.DeviceCookieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final KycService kycService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceCookieService deviceCookieService;

    @GetMapping("/health")
    public String health() {
        return "ms-users OK";
    }

    @GetMapping("/me/settings")
    public ResponseEntity<SettingsResponse> getSettings(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(new SettingsResponse(
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getIdNumber(),
                user.isMarketingEmailsOptIn(),
                user.isSystemEmailsOptIn()));
    }

    @PatchMapping("/me")
    public ResponseEntity<SettingsResponse> updateProfile(@RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        if (request.firstName() != null)
            user.setFirstName(request.firstName());
        if (request.lastName() != null)
            user.setLastName(request.lastName());
        if (request.address() != null)
            user.setAddress(request.address());
        if (request.idNumber() != null)
            user.setIdNumber(request.idNumber());
        if (request.marketingEmailsOptIn() != null)
            user.setMarketingEmailsOptIn(request.marketingEmailsOptIn());
        if (request.systemEmailsOptIn() != null)
            user.setSystemEmailsOptIn(request.systemEmailsOptIn());

        userRepository.save(user);

        return ResponseEntity.ok(new SettingsResponse(
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getIdNumber(),
                user.isMarketingEmailsOptIn(),
                user.isSystemEmailsOptIn()));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        // New password validation (avoid accepting weak passwords)
        String newPass = request.newPassword();
        if (newPass == null || newPass.trim().isEmpty() || newPass.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must be at least 6 characters long and cannot be empty.");
        }

        // Current password verification
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }

        // Safely update the password
        user.setPassword(passwordEncoder.encode(newPass));
        userRepository.save(user);

        // Revoke all existing device sessions to force re-login with the new password
        deviceCookieService.revokeAllDevices(user.getId());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/devices")
    public ResponseEntity<List<DeviceDto>> getTrustedDevices(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        // Retrieve all devices associated with the user and map them to a safe DTO
        List<DeviceDto> safeDevices = deviceCookieService.getUserDevices(user.getId())
                .stream()
                .map(device -> new DeviceDto(
                        device.getId(),
                        device.getUserAgent(),
                        device.getCreatedAt(),
                        device.getLastSeenAt()))
                .toList();

        // Return the list of safe device DTOs to the client
        return ResponseEntity.ok(safeDevices);
    }

    @DeleteMapping("/me/devices/{deviceId}")
    public ResponseEntity<Void> revokeDevice(@PathVariable String deviceId, Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        deviceCookieService.revokeDevice(user.getId(), deviceId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        // Soft delete flag
        user.setDeletedAt(LocalDateTime.now());

        // GPDR compliance
        user.setEmail("deleted_" + user.getId() + "_" + System.currentTimeMillis() + "@anonymized.local");
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setKycStatus(KycStatus.UNVERIFIED);

        // Destroy password to prevent any future access (even if the user tries to
        // recover the account, they won't be able to log in)
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));

        // Opt-out from all communications
        user.setMarketingEmailsOptIn(false);
        user.setSystemEmailsOptIn(false);

        userRepository.save(user);

        // Revoke all existing device sessions to log out from all devices immediately
        deviceCookieService.revokeAllDevices(user.getId());

        return ResponseEntity.ok().build();
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            username = s;
        }
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid principal");
        }
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    @PostMapping("/{userId}/kyc")
    public ResponseEntity<KycResponse> uploadIdentity(
            @PathVariable Long userId,
            @RequestParam("front") MultipartFile front,
            @RequestParam("back") MultipartFile back,
            Authentication authentication) {
        try {
            if (!isOwnerOrAdmin(authentication, userId)) {
                return ResponseEntity.status(403).body(new KycResponse(false, "Forbidden", null));
            }

            Optional<User> maybeUser = userRepository.findById(userId);
            if (maybeUser.isEmpty()) {
                return ResponseEntity.status(404).body(new KycResponse(false, "User not found", null));
            }

            String newJwt = kycService.processKyc(userId, front, back);
            if (newJwt != null) {
                return ResponseEntity.ok(new KycResponse(true, "User verified", newJwt));
            } else {
                return ResponseEntity.accepted()
                        .body(new KycResponse(false, "Document received; verification pending or rejected", null));
            }
        } catch (Exception e) {
            log.error("KYC verification failed for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(new KycResponse(false, "Verification failed. Please try again later.", null));
        }
    }

    @GetMapping("/{userId}/kyc/status")
    public ResponseEntity<KycResponse> kycStatus(@PathVariable Long userId, Authentication authentication) {
        if (!isOwnerOrAdmin(authentication, userId)) {
            return ResponseEntity.status(403).build();
        }

        Optional<User> maybe = userRepository.findById(userId);
        if (maybe.isEmpty())
            return ResponseEntity.notFound().build();
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
        if (authentication == null || !authentication.isAuthenticated())
            return false;
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            username = s;
        }
        if (username == null)
            return false;

        Optional<User> opt = userRepository.findByEmail(username);
        if (opt.isEmpty())
            return false;
        User user = opt.get();
        if (user.getId() != null && user.getId().equals(userId))
            return true;

        Collection<? extends GrantedAuthority> auths = authentication.getAuthorities();
        return auths.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable Long userId) {
        var u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserProfileDto profileDto = new UserProfileDto(
                u.getId(),
                null,
                u.getFirstName(),
                u.getLastName(),
                u.getKycStatus().name());

        return ResponseEntity.ok(profileDto);
    }

    @PostMapping("/{userId}/reviews")
    public ResponseEntity<ReviewDto> createReview(@PathVariable String userId, @RequestBody ReviewDto dto) {
        return ResponseEntity.ok(new ReviewDto("r1", dto.reviewerId(), userId, dto.contractId(), dto.rating(),
                dto.comment(), LocalDateTime.now()));
    }

    @GetMapping("/{userId}/reviews")
    public ResponseEntity<List<ReviewDto>> getUserReviews(@PathVariable String userId) {
        return ResponseEntity.ok(List.of(
                new ReviewDto("r1", "u2", userId, "c1", 5, "Great user", LocalDateTime.now())));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(Authentication authentication) {
        try {
            User u = getAuthenticatedUser(authentication);
            return ResponseEntity.ok(new UserProfileDto(u.getId(), u.getEmail(), u.getFirstName(),
                    u.getLastName(), u.getKycStatus().name()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public record SettingsResponse(String firstName, String lastName, String email, String address, String idNumber,
            boolean marketingEmailsOptIn, boolean systemEmailsOptIn) {
    }

    public record UpdateProfileRequest(String firstName, String lastName, String address, String idNumber,
            Boolean marketingEmailsOptIn, Boolean systemEmailsOptIn) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record DeviceDto(Long id, String userAgent, LocalDateTime createdAt, LocalDateTime lastSeenAt) {
    }
}