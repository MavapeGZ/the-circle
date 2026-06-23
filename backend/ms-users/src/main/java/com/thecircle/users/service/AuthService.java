package com.thecircle.users.service;

import com.thecircle.users.dto.AuthenticationRequest;
import com.thecircle.users.dto.AuthenticationResponse;
import com.thecircle.users.dto.RegisterRequest;
import com.thecircle.users.dto.VerifyOtpRequest;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authentication + registration with OTP email verification (signup) and
 * device-trust gated login OTP. Stateful OTPs live in {@link AuthOtpService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuthOtpService otpService;
    private final NotificationsClient notificationsClient;
    private final DeviceCookieService deviceCookieService;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final RefreshTokenService refreshTokenService;

    // i18n subject keys; ms-notifications resolves the localized subject per the
    // recipient's language. (Replaces the previous hardcoded @Value subjects.)
    private static final String VERIFY_SUBJECT_KEY = "email.subject.verify";
    private static final String LOGIN_SUBJECT_KEY = "email.subject.login";
    private static final String PASSWORD_RESET_SUBJECT_KEY = "email.subject.reset";

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        // Normalise the email once (trim + lowercase) so the uniqueness check and
        // the stored value match the case-insensitive lookups done at login and
        // password-reset time. Names are trimmed of stray surrounding whitespace.
        String email = request.getEmail() == null ? null
                : request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String firstName = request.getFirstName() == null ? null : request.getFirstName().trim();
        String lastName = request.getLastName() == null ? null : request.getLastName().trim();

        if (repository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .address(request.getAddress())
                .idNumber(request.getIdNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .kycStatus(KycStatus.UNVERIFIED)
                .emailVerified(false)
                .build();
        repository.save(user);

        AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                AuthOtpService.Purpose.EMAIL_VERIFICATION);
        sendOtpEmail(user, issued, VERIFY_SUBJECT_KEY, "account-verification");

        return AuthenticationResponse.builder()
                .sessionId(issued.sessionId)
                .requiresEmailVerification(true)
                .message("Verification code sent to " + user.getEmail())
                .build();
    }

    /**
     * Confirms the signup email-verification OTP. The device that completed the
     * OTP is the same one that just proved control of the mailbox, so it is
     * marked trusted here: a subsequent login from it skips the login OTP (see
     * issue #57). Returns JWT + device-trust token for the controller to set as
     * a cookie.
     */
    public OtpVerificationResult verifyEmail(VerifyOtpRequest request, String userAgent) {
        AuthOtpService.OtpSession session = otpService.consume(
                request.getSessionId(), request.getOtp(), AuthOtpService.Purpose.EMAIL_VERIFICATION);
        if (session == null) {
            throw new IllegalArgumentException(
                    "The verification code does not match or has expired. Please request a new code and try again.");
        }
        User user = repository.findById(session.userId)
                .orElseThrow(() -> new IllegalStateException("User not found for verification session"));
        user.setEmailVerified(true);
        repository.save(user);

        String deviceToken = deviceCookieService.issueDeviceCookie(user.getId(), userAgent);
        return new OtpVerificationResult(buildJwt(user), deviceToken, refreshTokenService.issue(user.getId()));
    }

    public LoginOutcome authenticate(AuthenticationRequest request, String deviceCookie) {
        // Spring Security will throw BadCredentialsException for unknown email or wrong password.
        User user = (User) authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()))
                .getPrincipal();
        if (!user.isEmailVerified()) {
            AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                    AuthOtpService.Purpose.EMAIL_VERIFICATION);
            sendOtpEmail(user, issued, VERIFY_SUBJECT_KEY, "account-verification");
            return LoginOutcome.intermediate(AuthenticationResponse.builder()
                    .sessionId(issued.sessionId)
                    .requiresEmailVerification(true)
                    .message("Email not verified. Verification code re-sent.")
                    .build());
        }

        if (deviceCookieService.isKnownDevice(user.getId(), deviceCookie)) {
            // Trusted device: skip the login OTP and mint the session directly.
            // A refresh token rides back so the controller sets the cookie.
            return LoginOutcome.authenticated(
                    AuthenticationResponse.builder().token(buildJwt(user)).build(),
                    refreshTokenService.issue(user.getId()));
        }

        AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                AuthOtpService.Purpose.LOGIN);
        sendOtpEmail(user, issued, LOGIN_SUBJECT_KEY, "login-otp");
        return LoginOutcome.intermediate(AuthenticationResponse.builder()
                .sessionId(issued.sessionId)
                .requiresOtp(true)
                .message("Sign-in code sent to " + user.getEmail())
                .build());
    }

    /**
     * Returns JWT + the new device-trust token to set as a cookie by the
     * controller.
     */
    public OtpVerificationResult verifyLoginOtp(VerifyOtpRequest request, String userAgent) {
        AuthOtpService.OtpSession session = otpService.consume(
                request.getSessionId(), request.getOtp(), AuthOtpService.Purpose.LOGIN);
        if (session == null) {
            throw new IllegalArgumentException(
                    "The sign-in code does not match or has expired. Please request a new code and try again.");
        }
        User user = repository.findById(session.userId)
                .orElseThrow(() -> new IllegalStateException("User not found for sign-in session"));

        String deviceToken = deviceCookieService.issueDeviceCookie(user.getId(), userAgent);
        return new OtpVerificationResult(buildJwt(user), deviceToken, refreshTokenService.issue(user.getId()));
    }

    /**
     * Exchanges a valid refresh token for a fresh access JWT, rotating the
     * refresh token in the process. Returns empty when the presented token is
     * unknown, expired or already rotated; the controller maps that to a 401 so
     * the client falls back to a full login.
     */
    public Optional<RefreshResult> refresh(String rawRefreshToken) {
        return refreshTokenService.rotate(rawRefreshToken).map(rotation -> {
            User user = repository.findById(rotation.userId)
                    .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));
            return new RefreshResult(buildJwt(user), rotation.rawToken);
        });
    }

    /** Logout: revoke the presented refresh token so it cannot be reused. */
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private String buildJwt(User user) {
        return jwtService.generateToken(user);
    }

    private void sendOtpEmail(User user, AuthOtpService.Issued issued, String subjectKey, String templateName) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("otpCode", issued.rawOtp);
        vars.put("ttlMinutes", Math.max(1, issued.ttlSeconds / 60));
        vars.put("recipientName", user.getFirstName());
        try {
            notificationsClient.sendEmail(user.getEmail(), subjectKey, templateName, user.getLanguage(), vars);
        } catch (NotificationsClient.DeliveryException e) {
            log.error("Failed to deliver OTP email to {}", user.getEmail(), e);
            throw e;
        }
    }

    /**
     * Result of a successful OTP verification (signup email-verification or
     * login second-factor): the JWT plus the device-trust token the controller
     * sets as the {@code tc_device} cookie so the device is remembered.
     */
    public static final class OtpVerificationResult {
        public final String token;
        public final String deviceToken;
        public final String refreshToken;

        public OtpVerificationResult(String token, String deviceToken, String refreshToken) {
            this.token = token;
            this.deviceToken = deviceToken;
            this.refreshToken = refreshToken;
        }
    }

    /**
     * Result of {@link #authenticate}. {@code refreshToken} is non-null only on
     * the fully-authenticated trusted-device path; the email-verification and
     * login-OTP branches return an intermediate response with no session yet.
     */
    public static final class LoginOutcome {
        public final AuthenticationResponse response;
        public final String refreshToken;

        private LoginOutcome(AuthenticationResponse response, String refreshToken) {
            this.response = response;
            this.refreshToken = refreshToken;
        }

        static LoginOutcome authenticated(AuthenticationResponse response, String refreshToken) {
            return new LoginOutcome(response, refreshToken);
        }

        static LoginOutcome intermediate(AuthenticationResponse response) {
            return new LoginOutcome(response, null);
        }
    }

    /** Result of a refresh: the new access JWT plus the rotated refresh token. */
    public static final class RefreshResult {
        public final String token;
        public final String refreshToken;

        public RefreshResult(String token, String refreshToken) {
            this.token = token;
            this.refreshToken = refreshToken;
        }
    }

    /**
     * Starts a password-reset flow. To avoid leaking which addresses are
     * registered, the response shape is the same whether the email exists or
     * not: a {@code sessionId} is always returned. Unknown emails get a random
     * UUID that has no backing OTP, so any later verify call against it fails
     * exactly like a wrong code.
     *
     * <p>Order matters: the lookup completes and the OTP is issued first; the
     * SMTP send happens after the closure returns. That keeps the lookup +
     * issue path off the critical timing window so a slow SMTP server cannot
     * become a side channel (slow response on real emails, fast on unknown
     * ones). The email is fired-and-forgotten and any delivery error is
     * swallowed for the same reason.
     */
    public ForgotPasswordResult requestPasswordReset(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return new ForgotPasswordResult(java.util.UUID.randomUUID().toString());
        }
        // Lookups are case-insensitive: a user who registered as `Foo@Example.com`
        // typing `foo@example.com` must still hit the row. We don't rely on the
        // database having a LOWER(email) index because the existing column is a
        // plain UNIQUE — normalising here matches the AuthOtpService login path.
        String normalised = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.Optional<User> userOpt = repository.findByEmail(normalised);
        if (userOpt.isEmpty()) {
            // Unknown email: fake session so the response timing/shape is identical.
            return new ForgotPasswordResult(java.util.UUID.randomUUID().toString());
        }
        User user = userOpt.get();
        AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                AuthOtpService.Purpose.PASSWORD_RESET);
        // SMTP happens outside the timing-sensitive path. The delivery failure
        // is logged but never surfaced — a bouncing mailbox would otherwise let
        // a caller distinguish a real email from an unknown one.
        try {
            sendOtpEmail(user, issued, PASSWORD_RESET_SUBJECT_KEY, "password-reset");
        } catch (NotificationsClient.DeliveryException e) {
            log.warn("Password reset email could not be sent to {}", user.getEmail());
        }
        return new ForgotPasswordResult(issued.sessionId);
    }

    /**
     * Validates the OTP and returns a short-lived single-use reset token. The
     * UI shows the new-password form only after this succeeds, so the user
     * never sees the password fields until they have proved ownership of the
     * email. The OTP itself is consumed here and cannot be replayed.
     *
     * <p>The {@code sourceIp} is recorded with the issued token so the final
     * {@code resetPassword} call has to come from the same client (see
     * {@link PasswordResetTokenStore}).
     */
    public VerifyResetOtpResult verifyResetOtp(String sessionId, String otp, String sourceIp) {
        AuthOtpService.OtpSession session = otpService.consume(sessionId, otp,
                AuthOtpService.Purpose.PASSWORD_RESET);
        if (session == null) {
            throw new IllegalArgumentException(
                    "The reset code does not match or has expired. Please request a new one and try again.");
        }
        String resetToken = passwordResetTokenStore.issue(session.userId, sourceIp);
        return new VerifyResetOtpResult(resetToken);
    }

    /**
     * Completes the password-reset flow. Consumes the short-lived reset token
     * (issued by {@link #verifyResetOtp}), BCrypt-hashes the new password, and
     * revokes all trusted-device cookies so the change kicks any cached
     * session off the user's other devices. The {@code sourceIp} must match
     * the one recorded at issue time when token-to-IP binding is enabled.
     */
    @Transactional
    public void resetPassword(String resetToken, String newPassword, String sourceIp) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException(
                    "New password must be at least 6 characters long.");
        }
        Long userId = passwordResetTokenStore.consume(resetToken, sourceIp);
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Your reset session has expired or was opened from a different device. Please start the password reset again.");
        }
        User user = repository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found for password reset token"));
        user.setPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
        deviceCookieService.revokeAllDevices(user.getId());
        // Kill every active session: refresh tokens outlive the access JWT, so
        // without this a pre-reset refresh token could still mint new sessions.
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password reset completed for user {} ({}); all trusted devices and refresh tokens revoked",
                user.getId(), user.getEmail());
    }

    public static final class ForgotPasswordResult {
        public final String sessionId;

        public ForgotPasswordResult(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    public static final class VerifyResetOtpResult {
        public final String resetToken;

        public VerifyResetOtpResult(String resetToken) {
            this.resetToken = resetToken;
        }
    }
}
