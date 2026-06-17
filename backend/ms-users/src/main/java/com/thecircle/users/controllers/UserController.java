package com.thecircle.users.controllers;

import com.thecircle.users.dto.KycResponse;
import com.thecircle.users.dto.ReviewDto;
import com.thecircle.users.service.AvatarService;
import com.thecircle.users.service.IbanCipher;
import com.thecircle.users.service.IbanValidator;
import com.thecircle.users.service.KycService;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.model.User;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.dto.UserProfileDto;
import com.thecircle.users.dto.PublicBadgeDto;
import com.thecircle.users.dto.PublicProfileDto;
import com.thecircle.users.service.CatalogClient;
import com.thecircle.users.service.ContractsClient;
import com.thecircle.users.service.DeviceCookieService;
import com.thecircle.users.service.GamificationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private static final Set<String> ALLOWED_ZONES = Set.of(
            "MADRID",
            "BARCELONA",
            "VALENCIA",
            "SEVILLE",
            "BILBAO_AND_SURROUNDINGS",
            "MALAGA",
            "OTHER");

    private final KycService kycService;
    private final AvatarService avatarService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceCookieService deviceCookieService;
    private final IbanCipher ibanCipher;
    private final CatalogClient catalogClient;
    private final ContractsClient contractsClient;
    private final GamificationClient gamificationClient;

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
            user.getZone(),
                user.getIdNumber(),
                user.getIbanLast4(),
                user.isMarketingEmailsOptIn(),
                user.isSystemEmailsOptIn(),
                avatarUrl(user)));
    }

    /**
     * Sets or replaces the user's payout IBAN. The plaintext IBAN is encrypted
     * at rest and never returned; only the last 4 digits are exposed for the
     * UI. Sending an empty/blank value clears the IBAN.
     */
    @PatchMapping("/me/iban")
    public ResponseEntity<IbanResponse> updateIban(@RequestBody UpdateIbanRequest request,
                                                   Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        String raw = request.iban();
        if (raw == null || raw.trim().isEmpty()) {
            user.setIbanEncrypted(null);
            user.setIbanLast4(null);
            userRepository.save(user);
            return ResponseEntity.ok(new IbanResponse(null));
        }
        String normalized = IbanValidator.normalize(raw);
        if (!IbanValidator.isValid(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid IBAN format.");
        }
        user.setIbanEncrypted(ibanCipher.encrypt(normalized));
        user.setIbanLast4(IbanValidator.last4(normalized));
        userRepository.save(user);
        return ResponseEntity.ok(new IbanResponse(user.getIbanLast4()));
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
        if (request.zone() != null)
            user.setZone(normalizeZone(request.zone()));
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
            user.getZone(),
                user.getIdNumber(),
                user.getIbanLast4(),
                user.isMarketingEmailsOptIn(),
                user.isSystemEmailsOptIn(),
                avatarUrl(user)));
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

        catalogClient.removeUserArticles(user.getId());
        contractsClient.removeOwnedOpenContracts(user.getId());

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

    /**
     * Uploads or replaces the authenticated user's profile picture. The image is
     * validated and stored by {@link AvatarService}; only the generated filename
     * is persisted. Returns the public URL the frontend can render.
     */
    @PostMapping("/me/avatar")
    public ResponseEntity<AvatarResponse> uploadAvatar(@RequestParam("file") MultipartFile file,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        try {
            String filename = avatarService.store(user.getId(), file);
            user.setProfilePicture(filename);
            userRepository.save(user);
            return ResponseEntity.ok(new AvatarResponse(avatarUrl(user)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Avatar upload failed for user {}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not save the picture. Please try again.");
        }
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<AvatarResponse> deleteAvatar(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        avatarService.delete(user.getId());
        user.setProfilePicture(null);
        userRepository.save(user);
        return ResponseEntity.ok(new AvatarResponse(null));
    }

    /**
     * Serves a user's profile picture bytes. Public (no auth) so a plain
     * {@code <img>} tag can load it — browsers cannot attach the JWT to image
     * requests. Only the opaque image is exposed; no profile data leaks here.
     */
    @GetMapping("/{userId}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long userId) {
        Optional<User> maybe = userRepository.findById(userId);
        if (maybe.isEmpty() || maybe.get().getDeletedAt() != null
                || maybe.get().getProfilePicture() == null) {
            return ResponseEntity.notFound().build();
        }
        User user = maybe.get();
        try {
            byte[] bytes = avatarService.load(userId, user.getProfilePicture());
            if (bytes == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(avatarService.mediaTypeFor(user.getProfilePicture()))
                    .body(bytes);
        } catch (Exception e) {
            log.error("Failed to read avatar for user {}", userId, e);
            return ResponseEntity.notFound().build();
        }
    }

    /** Public URL for a user's avatar, or null when none is set. */
    private String avatarUrl(User user) {
        if (user.getProfilePicture() == null) {
            return null;
        }
        // Cache-bust with the stored filename (it carries a timestamp) so a
        // replaced picture is not masked by a stale cached image.
        return "/api/users/" + user.getId() + "/avatar?v=" + user.getProfilePicture();
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
        public ResponseEntity<PublicProfileDto> getUserProfile(@PathVariable Long userId) {
        return userRepository.findById(userId)
            .filter(user -> user.getDeletedAt() == null)
            .map(user -> ResponseEntity.ok(buildPublicProfile(user)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        @GetMapping("/public")
        public ResponseEntity<List<PublicProfileDto>> getPublicProfiles(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> uniqueIds = ids.stream().distinct().toList();
        List<User> users = userRepository.findAllById(uniqueIds).stream()
            .filter(user -> user.getDeletedAt() == null)
            .toList();
        var usersById = users.stream().collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
        var summariesById = gamificationClient.getSummaries(uniqueIds).stream()
            .collect(Collectors.toMap(GamificationClient.UserSummary::userId, summary -> summary,
                (left, right) -> left, LinkedHashMap::new));

        List<PublicProfileDto> profiles = uniqueIds.stream()
            .map(usersById::get)
            .filter(java.util.Objects::nonNull)
            .map(user -> buildPublicProfile(user, summariesById.get(user.getId())))
            .toList();

        return ResponseEntity.ok(profiles);
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
                u.getLastName(), u.getZone(), u.getKycStatus().name()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

        public record SettingsResponse(String firstName, String lastName, String email, String address, String zone,
            String idNumber, String ibanLast4, boolean marketingEmailsOptIn, boolean systemEmailsOptIn,
            String avatarUrl) {
    }

        public record UpdateProfileRequest(String firstName, String lastName, String address, String zone, String idNumber,
            Boolean marketingEmailsOptIn, Boolean systemEmailsOptIn) {
    }

    public record UpdateIbanRequest(String iban) {
        // Records auto-generate toString() with every component; that default
        // would dump the full IBAN if an instance is ever logged. Mask it.
        @Override
        public String toString() {
            return "UpdateIbanRequest{iban=***}";
        }
    }

    public record IbanResponse(String ibanLast4) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest{currentPassword=***, newPassword=***}";
        }
    }

    public record DeviceDto(Long id, String userAgent, LocalDateTime createdAt, LocalDateTime lastSeenAt) {
    }

    public record AvatarResponse(String avatarUrl) {
    }

    private PublicProfileDto buildPublicProfile(User user) {
        var summary = gamificationClient.getSummaries(List.of(user.getId())).stream().findFirst().orElse(null);
        return buildPublicProfile(user, summary);
    }

    private PublicProfileDto buildPublicProfile(User user, GamificationClient.UserSummary summary) {
        int points = summary != null ? summary.totalPoints() : 0;
        List<PublicBadgeDto> badges = summary != null
                ? summary.badges().stream()
                .map(badge -> new PublicBadgeDto(
                        badge.code(),
                        badge.name(),
                        badge.description(),
                        badge.iconUrl(),
                        badge.tier(),
                        badge.earnedAt()))
                .toList()
                : List.of();

        String displayName = (List.of(user.getFirstName(), user.getLastName()).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "))).trim();
        if (displayName.isBlank()) {
            displayName = "User " + user.getId();
        }

        return new PublicProfileDto(
                user.getId(),
                displayName,
                avatarUrl(user),
                user.getZone(),
                user.getCreatedAt(),
                points,
                badges);
    }

    private String normalizeZone(String zone) {
        String normalized = zone.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!ALLOWED_ZONES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid zone.");
        }
        return normalized;
    }
}