package com.thecircle.users.controllers;

import com.thecircle.users.dto.AuthenticationRequest;
import com.thecircle.users.dto.AuthenticationResponse;
import com.thecircle.users.dto.ForgotPasswordRequest;
import com.thecircle.users.dto.RegisterRequest;
import com.thecircle.users.dto.ResetPasswordRequest;
import com.thecircle.users.dto.VerifyOtpRequest;
import com.thecircle.users.i18n.Messages;
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
import org.springframework.http.ResponseCookie;
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
    private final Messages messages;

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
                            .message(messages.get("auth.email.deliveryFailed"))
                            .build());
        } catch (RuntimeException e) {
            log.error("Unexpected error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthenticationResponse.builder()
                            .message(messages.get("auth.server.unexpected"))
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
                    .message(messages.get("auth.email.verified"))
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
                    .body(AuthenticationResponse.builder().message(messages.get("auth.login.badCredentials")).build());
        } catch (NotificationsClient.DeliveryException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AuthenticationResponse.builder()
                            .message(messages.get("auth.login.deliveryFailed"))
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
                                    .message(messages.get("auth.session.expired"))
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
        return ResponseEntity.ok(AuthenticationResponse.builder().message(messages.get("auth.logout.done")).build());
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
                            .message(messages.get("auth.reset.rateLimited"))
                            .build());
        }
        AuthService.ForgotPasswordResult result = service.requestPasswordReset(
                request != null ? request.getEmail() : null);
        // Always 200 with a neutral message + sessionId. Whether the email is
        // registered or not is invisible to the caller.
        return ResponseEntity.ok(AuthenticationResponse.builder()
                .sessionId(result.sessionId)
                .message(messages.get("auth.reset.neutral"))
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
                    .message(messages.get("auth.reset.codeVerified"))
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
                    .message(messages.get("auth.reset.passwordUpdated"))
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
        // SameSite=None so the cookie survives the cross-site request from the
        // Vercel-hosted SPA to this API (different domains). None mandates Secure,
        // so the two are tied together: prod (HTTPS) uses None+Secure, local dev
        // (HTTP, same-site) falls back to Lax. Without this the browser defaults
        // to Lax and drops tc_device on /api/auth/login, so every login re-prompts
        // for the OTP even on a previously trusted device.
        setCookie(response, DeviceCookieService.COOKIE_NAME, token, "/",
                (int) java.time.Duration.ofDays(deviceCookieMaxAgeDays).getSeconds(), deviceCookieSecure);
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
        // Same cross-site reasoning as the device cookie: the SPA on Vercel calls
        // /api/auth/refresh cross-domain, so the cookie needs SameSite=None+Secure
        // (prod) to be sent; Lax (dev) covers the same-site localhost case.
        setCookie(response, REFRESH_COOKIE_NAME, token, REFRESH_COOKIE_PATH,
                (int) java.time.Duration.ofDays(refreshCookieMaxAgeDays).getSeconds(), refreshCookieSecure);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        setCookie(response, REFRESH_COOKIE_NAME, "", REFRESH_COOKIE_PATH, 0, refreshCookieSecure);
    }

    /**
     * Writes an httpOnly cookie with an explicit SameSite attribute. SameSite is
     * {@code None} when {@code secure} is set (cross-site prod over HTTPS) and
     * {@code Lax} otherwise (same-site local dev over HTTP, where None would be
     * rejected for lacking Secure). Uses {@link ResponseCookie} because
     * {@link Cookie} offers no portable SameSite setter here.
     */
    private void setCookie(HttpServletResponse response, String name, String value,
                           String path, int maxAgeSeconds, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite(secure ? "None" : "Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
