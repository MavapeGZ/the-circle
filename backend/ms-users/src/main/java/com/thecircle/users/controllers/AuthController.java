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
import jakarta.validation.Valid;
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

    /**
     * Whether to trust the {@code X-Forwarded-For} header when resolving the
     * client IP for rate-limit bookkeeping. Set to {@code true} only when a
     * trusted reverse proxy / load balancer always rewrites that header — if
     * ms-users is reachable directly, a client can spoof the header and bypass
     * the rate limit. Defaults to {@code false} (fail closed).
     */
    @Value("${auth.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Value("${auth.device.cookie.secure:false}")
    private boolean deviceCookieSecure;

    /**
     * Refresh-token cookie lifetime in days. Defaults to 30 to match the
     * server-side {@code jwt.refresh-expiration} default; keep the two aligned
     * so the cookie does not outlive (or under-live) the stored token.
     */
    @Value("${auth.refresh.cookie.max-age-days:30}")
    private int refreshCookieMaxAgeDays;

    @Value("${auth.refresh.cookie.secure:false}")
    private boolean refreshCookieSecure;

    /** Cookie carrying the refresh token. Scoped to the auth endpoints only. */
    private static final String REFRESH_COOKIE_NAME = "tc_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request) {
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
    public ResponseEntity<AuthenticationResponse> verifyEmail(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            AuthService.OtpVerificationResult result =
                    service.verifyEmail(request, httpRequest.getHeader("User-Agent"));
            attachDeviceCookie(httpResponse, result.deviceToken);
            attachRefreshCookie(httpResponse, result.refreshToken);
            return ResponseEntity.ok(AuthenticationResponse.builder()
                    .token(result.token)
                    .message("Email verified")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String deviceCookie = readDeviceCookie(httpRequest);
        try {
            AuthService.LoginOutcome outcome = service.authenticate(request, deviceCookie);
            // Only the trusted-device path returns a session (token + refresh).
            if (outcome.refreshToken != null) {
                attachRefreshCookie(httpResponse, outcome.refreshToken);
            }
            return ResponseEntity.ok(outcome.response);
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
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            AuthService.OtpVerificationResult result = service.verifyLoginOtp(request, httpRequest.getHeader("User-Agent"));
            attachDeviceCookie(httpResponse, result.deviceToken);
            attachRefreshCookie(httpResponse, result.refreshToken);
            return ResponseEntity.ok(AuthenticationResponse.builder().token(result.token).build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    /**
     * Exchanges the httpOnly {@code tc_refresh} cookie for a fresh access JWT,
     * rotating the refresh token. Returns 401 (and clears the cookie) when the
     * refresh token is missing, expired or already used, so the frontend falls
     * back to a full login. See issue #89.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshCookie = readRefreshCookie(httpRequest);
        return service.refresh(refreshCookie)
                .map(result -> {
                    attachRefreshCookie(httpResponse, result.refreshToken);
                    return ResponseEntity.ok(AuthenticationResponse.builder().token(result.token).build());
                })
                .orElseGet(() -> {
                    clearRefreshCookie(httpResponse);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(AuthenticationResponse.builder()
                                    .message("Your session has expired. Please sign in again.")
                                    .build());
                });
    }

    /**
     * Revokes the current refresh token and clears its cookie. Always 200 so a
     * client can log out cleanly even with a missing/stale cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<AuthenticationResponse> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        service.logout(readRefreshCookie(httpRequest));
        clearRefreshCookie(httpResponse);
        return ResponseEntity.ok(AuthenticationResponse.builder().message("Logged out").build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthenticationResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
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
    public ResponseEntity<AuthenticationResponse> verifyResetOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                                 HttpServletRequest httpRequest) {
        try {
            String sourceIp = resolveClientIp(httpRequest);
            AuthService.VerifyResetOtpResult result = service.verifyResetOtp(
                    request.getSessionId(), request.getOtp(), sourceIp);
            // Response shape reuse: the opaque reset token rides in `sessionId`
            // so the frontend's existing AuthenticationResponse plumbing covers
            // it with no extra field. Step 3 (new password) sends the value
            // back as `resetToken`. The token is single-use, IP-bound and has a
            // short TTL — see PasswordResetTokenStore for the binding rules.
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
    public ResponseEntity<AuthenticationResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                                HttpServletRequest httpRequest) {
        try {
            String sourceIp = resolveClientIp(httpRequest);
            service.resetPassword(request.getResetToken(), request.getNewPassword(), sourceIp);
            return ResponseEntity.ok(AuthenticationResponse.builder()
                    .message("Password updated. You can now sign in with the new password.")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AuthenticationResponse.builder().message(e.getMessage()).build());
        }
    }

    /**
     * Resolves the client IP for rate-limit bookkeeping. {@code X-Forwarded-For}
     * is client-spoofable when ms-users is reachable directly, so the header is
     * only consulted when {@code auth.trust-forwarded-for=true}, which a
     * deployment must opt into after confirming a trusted reverse proxy always
     * rewrites the header. Otherwise {@code getRemoteAddr()} is used as the
     * source of truth.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                int comma = header.indexOf(',');
                return comma > 0 ? header.substring(0, comma).trim() : header.trim();
            }
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

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (REFRESH_COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void attachRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setMaxAge((int) java.time.Duration.ofDays(refreshCookieMaxAgeDays).getSeconds());
        cookie.setSecure(refreshCookieSecure);
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setMaxAge(0);
        cookie.setSecure(refreshCookieSecure);
        response.addCookie(cookie);
    }
}
