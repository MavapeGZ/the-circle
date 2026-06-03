package com.thecircle.users.controllers;

import com.thecircle.users.dto.AuthenticationRequest;
import com.thecircle.users.dto.AuthenticationResponse;
import com.thecircle.users.dto.RegisterRequest;
import com.thecircle.users.dto.VerifyOtpRequest;
import com.thecircle.users.service.AuthService;
import com.thecircle.users.service.DeviceCookieService;
import com.thecircle.users.service.NotificationsClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthenticationResponse.builder()
                            .message("Unexpected server error: " + e.getMessage())
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
