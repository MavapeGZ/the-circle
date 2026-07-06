package com.thecircle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring Cloud Gateway context must start with the bundled
 * configuration. This fails fast if a route, predicate or CORS setting in
 * {@code application.yml} becomes malformed.
 */
@SpringBootTest
class GatewayApplicationTest {

    @Test
    void contextLoads() {
        // Intentionally empty: success means the application context started.
    }
}
