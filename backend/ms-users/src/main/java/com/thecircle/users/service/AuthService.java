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

    @Value("${signature.mail.verify-subject:The Circle - Verify your account}")
    private String verifySubject;

    @Value("${signature.mail.login-subject:The Circle - Sign-in code}")
    private String loginSubject;

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (repository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .kycStatus(KycStatus.UNVERIFIED)
                .emailVerified(false)
                .build();
        repository.save(user);

        AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                AuthOtpService.Purpose.EMAIL_VERIFICATION);
        sendOtpEmail(user, issued, verifySubject, "account-verification");

        return AuthenticationResponse.builder()
                .sessionId(issued.sessionId)
                .requiresEmailVerification(true)
                .message("Verification code sent to " + user.getEmail())
                .build();
    }

    public AuthenticationResponse verifyEmail(VerifyOtpRequest request) {
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

        return AuthenticationResponse.builder()
                .token(buildJwt(user))
                .message("Email verified")
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request, String deviceCookie) {
        // Spring Security will throw BadCredentialsException for unknown email or wrong password.
        User user = (User) authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()))
                .getPrincipal();
        if (!user.isEmailVerified()) {
            AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                    AuthOtpService.Purpose.EMAIL_VERIFICATION);
            sendOtpEmail(user, issued, verifySubject, "account-verification");
            return AuthenticationResponse.builder()
                    .sessionId(issued.sessionId)
                    .requiresEmailVerification(true)
                    .message("Email not verified. Verification code re-sent.")
                    .build();
        }

        if (deviceCookieService.isKnownDevice(user.getId(), deviceCookie)) {
            return AuthenticationResponse.builder().token(buildJwt(user)).build();
        }

        AuthOtpService.Issued issued = otpService.issue(user.getId(), user.getEmail(),
                AuthOtpService.Purpose.LOGIN);
        sendOtpEmail(user, issued, loginSubject, "login-otp");
        return AuthenticationResponse.builder()
                .sessionId(issued.sessionId)
                .requiresOtp(true)
                .message("Sign-in code sent to " + user.getEmail())
                .build();
    }

    /**
     * Returns JWT + the new device-trust token to set as a cookie by the
     * controller.
     */
    public LoginOtpResult verifyLoginOtp(VerifyOtpRequest request, String userAgent) {
        AuthOtpService.OtpSession session = otpService.consume(
                request.getSessionId(), request.getOtp(), AuthOtpService.Purpose.LOGIN);
        if (session == null) {
            throw new IllegalArgumentException(
                    "The sign-in code does not match or has expired. Please request a new code and try again.");
        }
        User user = repository.findById(session.userId)
                .orElseThrow(() -> new IllegalStateException("User not found for sign-in session"));

        String deviceToken = deviceCookieService.issueDeviceCookie(user.getId(), userAgent);
        return new LoginOtpResult(buildJwt(user), deviceToken);
    }

    private String buildJwt(User user) {
        return jwtService.generateToken(user);
    }

    private void sendOtpEmail(User user, AuthOtpService.Issued issued, String subject, String templateName) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("otpCode", issued.rawOtp);
        vars.put("ttlMinutes", Math.max(1, issued.ttlSeconds / 60));
        vars.put("recipientName", user.getFirstName());
        vars.put("subject", subject);
        try {
            notificationsClient.sendEmail(user.getEmail(), subject, templateName, vars);
        } catch (NotificationsClient.DeliveryException e) {
            log.error("Failed to deliver OTP email to {}", user.getEmail(), e);
            throw e;
        }
    }

    public static final class LoginOtpResult {
        public final String token;
        public final String deviceToken;

        public LoginOtpResult(String token, String deviceToken) {
            this.token = token;
            this.deviceToken = deviceToken;
        }
    }
}
