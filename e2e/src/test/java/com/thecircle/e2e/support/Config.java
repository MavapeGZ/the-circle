package com.thecircle.e2e.support;

public final class Config {
    private Config() {
    }

    public static String baseUrl() {
        return env("E2E_BASE_URL", "http://localhost:5173");
    }

    public static String loginUrl() {
        return env("E2E_LOGIN_URL", baseUrl() + "/login");
    }

    public static long timeoutSeconds() {
        return Long.parseLong(env("E2E_TIMEOUT_SECONDS", "60"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}