package com.thecircle.users.controllers;

import com.thecircle.users.dto.AuthenticationRequest;
import com.thecircle.users.dto.AuthenticationResponse;
import com.thecircle.users.dto.ForgotPasswordRequest;
import com.thecircle.users.dto.RegisterRequest;
import com.thecircle.users.dto.ResetPasswordRequest;
import com.thecircle.users.dto.VerifyOtpRequest;
import com.thecircle.users.service.AuthService;
import com.thecircle.users.service.DeviceCookieService;
import com.thecircle.users.service.NotificationsClient;
import com.thecircle.users.service.PasswordResetRateLimiter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.thecircle.users.service.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService service;
    private final PasswordResetRateLimiter passwordResetRateLimiter;

    @Value("${auth.device.cookie.max-age-days:90}")
    private int deviceCookieMaxAgeDays;

    @Value("${auth.device.cookie.secure:false}")
    private boolean deviceCookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(service.register(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        } catch (NotificationsClient.DeliveryException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AuthenticationResponse.builder()
                            .message("Could not send the verification email. Please try again in a moment.")
                            .build());
        } catch (RuntimeException e) {
            log.error("Unexpected error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthenticationResponse.builder()
                            .message("Unexpected server error. Please try again.")
                            .build());
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthenticationResponse> verifyEmail(@RequestBody VerifyOtpRequest request) {
        try {
            return ResponseEntity.ok(service.verifyEmail(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest) {
        String deviceCookie = readDeviceCookie(httpRequest);
        try {
            return ResponseEntity.ok(service.authenticate(request, deviceCookie));
        } catch (AccountNotFoundException | BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthenticationResponse.builder().message("Incorrect email or password.").build());
        } catch (NotificationsClient.DeliveryException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AuthenticationResponse.builder()
                            .message("Could not send the sign-in code. Please try again in a moment.")
                            .build());
        }
    }

    @PostMapping("/login-otp")
    public ResponseEntity<AuthenticationResponse> verifyLoginOtp(
            @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            AuthService.LoginOtpResult result = service.verifyLoginOtp(request, httpRequest.getHeader("User-Agent"));
            attachDeviceCookie(httpResponse, result.deviceToken);
            return ResponseEntity.ok(AuthenticationResponse.builder().token(result.token).build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthenticationResponse> forgotPassword(
            @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        // Rate-limit per IP. 429 is the only response code that can leak "the
        // request was rejected before reaching the lookup", but it is shared
        // across known and unknown emails (it triggers on the source IP, not
        // the address), so it does not enable enumeration.
        if (!passwordResetRateLimiter.tryAcquire(resolveClientIp(httpRequest))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(AuthenticationResponse.builder()
                            .message("Too many password reset requests. Please wait a few minutes and try again.")
                            .build());
        }
        AuthService.ForgotPasswordResult result = service.requestPasswordReset(
                request != null ? request.getEmail() : null);
        // Always 200 with a neutral message + sessionId. Whether the email is
        // registered or not is invisible to the caller.
        return ResponseEntity.ok(AuthenticationResponse.builder()
                .sessionId(result.sessionId)
                .message("If that email is registered, a reset code is on its way.")
                .build());
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<AuthenticationResponse> verifyResetOtp(@RequestBody VerifyOtpRequest request) {
        try {
            AuthService.VerifyResetOtpResult result = service.verifyResetOtp(
                    request.getSessionId(), request.getOtp());
            // The opaque reset token rides in `sessionId` to reuse the response
            // shape and keep the frontend client small. Step 3 (new password)
            // sends it back as `resetToken`.
            return ResponseEntity.ok(AuthenticationResponse.builder()
                    .sessionId(result.resetToken)
                    .message("Code verified. You can now choose a new password.")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthenticationResponse> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            service.resetPassword(request.getResetToken(), request.getNewPassword());
            return ResponseEntity.ok(AuthenticationResponse.builder()
                    .message("Password updated. You can now sign in with the new password.")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    /**
     * Best-effort client IP for rate-limiting. Trusts the first hop in
     * {@code X-Forwarded-For} when present (assumes a reverse proxy in front);
     * falls back to {@code getRemoteAddr()} for direct connections.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            int comma = header.indexOf(',');
            return comma > 0 ? header.substring(0, comma).trim() : header.trim();
        }
        return request.getRemoteAddr();
    }

    private String readDeviceCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (DeviceCookieService.COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void attachDeviceCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(DeviceCookieService.COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) java.time.Duration.ofDays(deviceCookieMaxAgeDays).getSeconds());
        cookie.setSecure(deviceCookieSecure);
        response.addCookie(cookie);
    }
}
