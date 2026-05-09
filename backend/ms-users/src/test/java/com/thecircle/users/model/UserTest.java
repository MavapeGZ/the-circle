package com.thecircle.users.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void applyLegacyKycVerified_backfillsKycStatusWhenLegacyUserWasVerified() throws Exception {
        User user = new User();
        user.setKycStatus(KycStatus.UNVERIFIED);
        setLegacyKycVerified(user, true);

        invokeLifecycleMethod(user, "applyLegacyKycVerified");

        assertEquals(KycStatus.VERIFIED, user.getKycStatus());
    }

    @Test
    void onUpdate_syncsLegacyKycVerifiedFromEnumStatus() throws Exception {
        User user = User.builder()
                .kycStatus(KycStatus.REJECTED)
                .build();

        invokeLifecycleMethod(user, "onUpdate");

        assertFalse(getLegacyKycVerified(user));

        user.setKycStatus(KycStatus.VERIFIED);
        invokeLifecycleMethod(user, "onUpdate");

        assertTrue(getLegacyKycVerified(user));
    }

    private static void invokeLifecycleMethod(User user, String methodName) throws Exception {
        Method method = User.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(user);
    }

    private static void setLegacyKycVerified(User user, boolean value) throws Exception {
        Field field = User.class.getDeclaredField("legacyKycVerified");
        field.setAccessible(true);
        field.set(user, value);
    }

    private static boolean getLegacyKycVerified(User user) throws Exception {
        Field field = User.class.getDeclaredField("legacyKycVerified");
        field.setAccessible(true);
        return Boolean.TRUE.equals(field.get(user));
    }
}
