package com.thecircle.contracts.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Fails fast if signature OTP codes would be returned in HTTP responses outside
 * a developer machine. {@code signature.otp.expose-in-response} is a test-only
 * convenience (E2E reads the code from the JSON body); it defeats the advanced
 * e-signature factor, so it must never be active on a deployed profile.
 *
 * <p>Allowed only when the {@code local} or {@code test} profile is active.
 * Any other combination (including no profile, or a {@code dev}/{@code prod}
 * deployment with {@code OTP_EXPOSE_DEV=true}) aborts startup.
 */
@Component
public class OtpExposureGuard {

    private static final List<String> ALLOWED_PROFILES = List.of("local", "test");

    private final Environment environment;

    @Value("${signature.otp.expose-in-response:false}")
    private boolean exposeOtp;

    public OtpExposureGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        if (!exposeOtp) {
            return;
        }
        boolean onAllowedProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(ALLOWED_PROFILES::contains);
        if (!onAllowedProfile) {
            throw new IllegalStateException(
                    "signature.otp.expose-in-response=true is only permitted under the 'local' or 'test' "
                            + "profile (active profiles: "
                            + Arrays.toString(environment.getActiveProfiles())
                            + "). Unset OTP_EXPOSE_DEV before deploying.");
        }
    }
}
