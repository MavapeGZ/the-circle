package com.thecircle.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the declarative routing table defined in {@code application.yml}.
 * <p>
 * The gateway is the single entry point in front of every microservice, so a
 * wrong path predicate or target URI silently breaks a whole feature. These
 * tests assert the route ids, their {@code Path} predicates and their target
 * URIs (resolved to the in-code defaults when no environment variable is set).
 */
@SpringBootTest
class GatewayRoutesConfigTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    private Map<String, RouteDefinition> routesById() {
        List<RouteDefinition> definitions = routeDefinitionLocator
                .getRouteDefinitions()
                .collectList()
                .block();
        assertThat(definitions).isNotNull();
        return definitions.stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
    }

    /** Extracts the single argument of the {@code Path} predicate of a route. */
    private static String pathPredicateOf(RouteDefinition route) {
        PredicateDefinition path = route.getPredicates().stream()
                .filter(p -> "Path".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "route '" + route.getId() + "' has no Path predicate"));
        return path.getArgs().values().iterator().next();
    }

    @Test
    void exactlyTheExpectedRoutesAreDefined() {
        assertThat(routesById().keySet()).containsExactlyInAnyOrder(
                "ms-users-auth",
                "ms-users",
                "ms-catalog",
                "ms-contracts",
                "ms-contracts-chat",
                "ms-gamification",
                "ms-notifications");
    }

    @ParameterizedTest(name = "{0} -> {1} @ {2}")
    @CsvSource({
            "ms-users-auth,     /api/auth/**,          http://localhost:8081",
            "ms-users,          /api/users/**,         http://localhost:8081",
            "ms-catalog,        /api/catalog/**,       http://localhost:8082",
            "ms-contracts,      /api/contracts/**,     http://localhost:8083",
            "ms-contracts-chat, /api/chat/**,          http://localhost:8083",
            "ms-gamification,   /api/gamification/**,  http://localhost:8084",
            "ms-notifications,  /api/notifications/**, http://localhost:8085"
    })
    void route_mapsPathToExpectedService(String id, String expectedPath, String expectedUri) {
        RouteDefinition route = routesById().get(id);
        assertThat(route).as("route '%s' must be defined", id).isNotNull();
        assertThat(pathPredicateOf(route)).isEqualTo(expectedPath);
        assertThat(route.getUri()).hasToString(expectedUri);
    }
}
