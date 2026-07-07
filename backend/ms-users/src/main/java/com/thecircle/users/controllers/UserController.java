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
import com.thecircle.users.service.RefreshTokenService;
import com.thecircle.users.service.GamificationClient;
import com.thecircle.users.service.UploadValidation;
import com.thecircle.users.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    // Locale preference allowlists. Kept in sync with the frontend selectors and
    // the backend i18n bundles; invalid values are rejected at the API boundary.
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("es", "en");
    private static final Set<String> ALLOWED_CURRENCIES = Set.of("EUR", "USD", "GBP");
    private static final Set<String> ALLOWED_TIMEZONES = Set.of(
            "Europe/Madrid",
            "Europe/London",
            "Atlantic/Canary",
            "UTC",
            "America/New_York",
            "America/Los_Angeles");

    // Each KYC scan capped at 5 MB; aligns with spring.servlet.multipart.max-file-size.
    private static final long MAX_KYC_BYTES = 5L * 1024 * 1024;

    private final KycService kycService;
    private final AvatarService avatarService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceCookieService deviceCookieService;
    private final RefreshTokenService refreshTokenService;
    private final IbanCipher ibanCipher;
    private final CatalogClient catalogClient;
    private final ContractsClient contractsClient;
    private final GamificationClient gamificationClient;
    private final com.thecircle.users.service.ReviewService reviewService;
    private final com.thecircle.users.i18n.Messages messages;

    @GetMapping("/health")
    public String health() {
        return "ms-users OK";
    }

    @GetMapping("/me/settings")
    public ResponseEntity<SettingsResponse> getSettings(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(settingsResponse(user));
    }

    /**
     * Sets or replaces the user's payout IBAN. The plaintext IBAN is encrypted
     * at rest and never returned; only the last 4 digits are exposed for the
     * UI. Sending an empty/blank value clears the IBAN.
     */
    @PatchMapping("/me/iban")
    public ResponseEntity<IbanResponse> updateIban(@Valid @RequestBody UpdateIbanRequest request,
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("user.iban.invalid"));
        }
        user.setIbanEncrypted(ibanCipher.encrypt(normalized));
        user.setIbanLast4(IbanValidator.last4(normalized));
        userRepository.save(user);
        return ResponseEntity.ok(new IbanResponse(user.getIbanLast4()));
    }

    @PatchMapping("/me")
    public ResponseEntity<SettingsResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
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
        if (request.language() != null)
            user.setLanguage(normalizeChoice(request.language().toLowerCase(), ALLOWED_LANGUAGES, "language"));
        if (request.currency() != null)
            user.setCurrency(normalizeChoice(request.currency().toUpperCase(), ALLOWED_CURRENCIES, "currency"));
        if (request.timezone() != null)
            user.setTimezone(normalizeChoice(request.timezone(), ALLOWED_TIMEZONES, "timezone"));

        userRepository.save(user);

        return ResponseEntity.ok(settingsResponse(user));
    }

    private SettingsResponse settingsResponse(User user) {
        return new SettingsResponse(
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAddress(),
                user.getZone(),
                user.getIdNumber(),
                user.getIbanLast4(),
                user.isMarketingEmailsOptIn(),
                user.isSystemEmailsOptIn(),
                avatarUrl(user),
                user.getLanguage(),
                user.getCurrency(),
                user.getTimezone());
    }

    private String normalizeChoice(String value, Set<String> allowed, String field) {
        if (!allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("user.field.invalid", field));
        }
        return value;
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        // Strength is enforced by @Valid on ChangePasswordRequest (same rule as
        // registration). Here we only verify the current password matches.
        String newPass = request.newPassword();

        // Current password verification
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("user.password.incorrect"));
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
        // Refresh tokens outlive the access JWT, so revoke them too — otherwise a
        // held refresh cookie could keep minting sessions for the deleted account.
        refreshTokenService.revokeAllForUser(user.getId());

        catalogClient.removeUserArticles(user.getId());
        contractsClient.removeOwnedOpenContracts(user.getId());
        // Drop reviews written by or about the user so none outlive the account.
        reviewService.deleteAllForUser(user.getId());

        return ResponseEntity.ok().build();
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, messages.get("common.notAuthenticated"));
        }
        Object principal = authentication.getPrincipal();
        String username = null;
        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            username = s;
        }
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, messages.get("common.invalidPrincipal"));
        }
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, messages.get("common.userNotFound")));
    }

    @PostMapping("/{userId}/kyc")
    public ResponseEntity<KycResponse> uploadIdentity(
            @PathVariable Long userId,
            @RequestParam("front") MultipartFile front,
            @RequestParam("back") MultipartFile back,
            Authentication authentication) {
        try {
            if (!isOwnerOrAdmin(authentication, userId)) {
                return ResponseEntity.status(403).body(new KycResponse(false, messages.get("kyc.forbidden"), null));
            }

            Optional<User> maybeUser = userRepository.findById(userId);
            if (maybeUser.isEmpty()) {
                return ResponseEntity.status(404).body(new KycResponse(false, messages.get("common.userNotFound"), null));
            }

            // Validate both documents at the upload boundary: non-empty, <=5 MB,
            // single safe extension (jpg/jpeg/png/pdf), declared type and real
            // magic bytes all agreeing. Rejects disguised/oversized uploads.
            UploadValidation.validate(front, "front", UploadValidation.DOCUMENT_TYPES, MAX_KYC_BYTES);
            UploadValidation.validate(back, "back", UploadValidation.DOCUMENT_TYPES, MAX_KYC_BYTES);

            String newJwt = kycService.processKyc(userId, front, back);
            if (newJwt != null) {
                return ResponseEntity.ok(new KycResponse(true, messages.get("kyc.verified"), newJwt));
            } else {
                return ResponseEntity.accepted()
                        .body(new KycResponse(false, messages.get("kyc.pending"), null));
            }
        } catch (IllegalArgumentException e) {
            // File validation failure (empty / too large / wrong type / bad magic bytes).
            return ResponseEntity.badRequest().body(new KycResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("KYC verification failed for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(new KycResponse(false, messages.get("kyc.failed"), null));
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
            // Surface the precise validation reason (bad name, type, size) in the
            // body so the frontend can show it instead of a generic fallback.
            return ResponseEntity.badRequest().body(new AvatarResponse(null, e.getMessage()));
        } catch (Exception e) {
            log.error("Avatar upload failed for user {}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("user.avatar.saveFailed"));
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

    /**
     * Public profile by opaque public id. This is the only profile route exposed
     * to anonymous callers (see SecurityConfig); the numeric {@code /{userId}}
     * variant is authenticated and used internally, so profiles can no longer be
     * enumerated by walking sequential primary keys.
     */
    @GetMapping("/by-public-id/{publicId}")
    public ResponseEntity<PublicProfileDto> getUserProfileByPublicId(@PathVariable String publicId) {
        return userRepository.findByPublicId(publicId)
                .filter(user -> user.getDeletedAt() == null)
                .map(user -> ResponseEntity.ok(buildPublicProfile(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
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
        // One aggregate query for all requested users instead of two per user.
        var reviewStatsById = reviewService.statsForTargets(uniqueIds);
        var emptyStats = new com.thecircle.users.service.ReviewService.ReviewStats(null, 0);

        List<PublicProfileDto> profiles = uniqueIds.stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(user -> buildPublicProfile(user, summariesById.get(user.getId()),
                        reviewStatsById.getOrDefault(user.getId(), emptyStats)))
                .toList();

        return ResponseEntity.ok(profiles);
    }

    @PostMapping("/{userId}/reviews")
    public ResponseEntity<ReviewDto> createReview(@PathVariable Long userId, @Valid @RequestBody ReviewDto dto,
            Authentication authentication) {
        User reviewer = getAuthenticatedUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(reviewer.getId(), userId, dto));
    }

    @GetMapping("/{userId}/reviews")
    public ResponseEntity<List<ReviewDto>> getUserReviews(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewService.listForTarget(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(Authentication authentication) {
        try {
            User u = getAuthenticatedUser(authentication);
            return ResponseEntity.ok(new UserProfileDto(u.getId(), u.getPublicId(), u.getEmail(), u.getFirstName(),
                    u.getLastName(), u.getZone(), u.getKycStatus().name(), avatarUrl(u)));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public record SettingsResponse(String firstName, String lastName, String email, String address, String zone,
            String idNumber, String ibanLast4, boolean marketingEmailsOptIn, boolean systemEmailsOptIn,
            String avatarUrl, String language, String currency, String timezone) {
    }

    public record UpdateProfileRequest(
            @Pattern(regexp = ValidationPatterns.NAME, message = "validation.firstName.pattern")
            String firstName,
            @Pattern(regexp = ValidationPatterns.NAME, message = "validation.lastName.pattern")
            String lastName,
            @Size(max = 255, message = "validation.address.size")
            @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.address.noAngle")
            String address,
            @Size(max = 64, message = "validation.zone.size")
            String zone,
            @Size(max = 50, message = "validation.idNumber.size")
            @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.idNumber.noAngle")
            String idNumber,
            Boolean marketingEmailsOptIn, Boolean systemEmailsOptIn,
            @Size(max = 8, message = "validation.language.size") String language,
            @Size(max = 3, message = "validation.currency.size") String currency,
            @Size(max = 64, message = "validation.timezone.size") String timezone) {
    }

    public record UpdateIbanRequest(
            @Size(max = 34, message = "validation.iban.size")
            String iban) {
        // Records auto-generate toString() with every component; that default
        // would dump the full IBAN if an instance is ever logged. Mask it.
        @Override
        public String toString() {
            return "UpdateIbanRequest{iban=***}";
        }
    }

    public record IbanResponse(String ibanLast4) {
    }

    public record ChangePasswordRequest(
            @jakarta.validation.constraints.NotBlank(message = "validation.currentPassword.required")
            String currentPassword,
            @jakarta.validation.constraints.NotBlank(message = "validation.newPassword.required")
            @Pattern(regexp = ValidationPatterns.PASSWORD, message = "validation.password.pattern")
            String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest{currentPassword=***, newPassword=***}";
        }
    }

    public record DeviceDto(Long id, String userAgent, LocalDateTime createdAt, LocalDateTime lastSeenAt) {
    }

    public record AvatarResponse(String avatarUrl, String message) {
        public AvatarResponse(String avatarUrl) {
            this(avatarUrl, null);
        }
    }

    private PublicProfileDto buildPublicProfile(User user) {
        var summary = gamificationClient.getSummaries(List.of(user.getId())).stream().findFirst().orElse(null);
        return buildPublicProfile(user, summary, reviewService.statsForTarget(user.getId()));
    }

    private PublicProfileDto buildPublicProfile(User user, GamificationClient.UserSummary summary,
            com.thecircle.users.service.ReviewService.ReviewStats reviewStats) {
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
                user.getPublicId(),
                displayName,
                avatarUrl(user),
                user.getZone(),
                user.getCreatedAt(),
                points,
                badges,
                reviewStats.average(),
                reviewStats.count());
    }

    private String normalizeZone(String zone) {
        String normalized = zone.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!ALLOWED_ZONES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("user.zone.invalid"));
        }
        return normalized;
    }
}
