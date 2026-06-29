package com.thecircle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the global CORS policy declared in {@code application.yml}. The
 * gateway is the single public entry point in front of the Front-End, so a
 * wrong CORS setting would break every browser call. These tests drive a real
 * preflight ({@code OPTIONS}) against the running gateway, which the CORS web
 * filter answers before any route is matched — no downstream service is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCorsConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private WebTestClient webClient;

    @Test
    void preflightFromAllowedOriginIsAccepted() {
        webClient.method(HttpMethod.OPTIONS)
                .uri("/api/catalog/articles")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    void preflightAdvertisesTheConfiguredMethods() {
        webClient.method(HttpMethod.OPTIONS)
                .uri("/api/users/profile")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> {
                    org.assertj.core.api.Assertions.assertThat(methods)
                            .contains("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
                });
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() {
        webClient.method(HttpMethod.OPTIONS)
                .uri("/api/catalog/articles")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }
}
