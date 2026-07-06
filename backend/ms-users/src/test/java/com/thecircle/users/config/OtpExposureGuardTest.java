package com.thecircle.users.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the single control that keeps OTP codes out of HTTP responses on a
 * deployed profile. If any of these break, the fail-fast protection is gone.
 */
class OtpExposureGuardTest {

    private OtpExposureGuard guard(boolean expose, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        OtpExposureGuard guard = new OtpExposureGuard(env);
        ReflectionTestUtils.setField(guard, "exposeOtpInResponse", expose);
        return guard;
    }

    @Test
    void exposureDisabled_anyProfile_startsUp() {
        assertDoesNotThrow(() -> guard(false).verify());
        assertDoesNotThrow(() -> guard(false, "prod").verify());
    }

    @Test
    void exposureEnabled_localProfile_startsUp() {
        assertDoesNotThrow(() -> guard(true, "local").verify());
    }

    @Test
    void exposureEnabled_testProfile_startsUp() {
        assertDoesNotThrow(() -> guard(true, "test").verify());
    }

    @Test
    void exposureEnabled_noProfile_abortsStartup() {
        assertThrows(IllegalStateException.class, () -> guard(true).verify());
    }

    @Test
    void exposureEnabled_deployedProfile_abortsStartup() {
        assertThrows(IllegalStateException.class, () -> guard(true, "prod").verify());
        assertThrows(IllegalStateException.class, () -> guard(true, "dev").verify());
    }
}
