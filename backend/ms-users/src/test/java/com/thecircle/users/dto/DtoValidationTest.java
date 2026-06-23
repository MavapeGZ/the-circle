package com.thecircle.users.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One rejection case per validated field across the user-facing DTOs, driven
 * directly through the bean Validator (no Spring context needed).
 */
class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean valid(Object dto) {
        return validator.validate(dto).isEmpty();
    }

    @Test
    void registerRequest_accepts_validInput() {
        assertTrue(valid(RegisterRequest.builder()
                .firstName("Ana").lastName("García")
                .email("ana@example.com").password("Str0ngPass")
                .build()));
    }

    @Test
    void registerRequest_rejects_blankName() {
        assertFalse(valid(RegisterRequest.builder()
                .firstName("").lastName("García")
                .email("ana@example.com").password("Str0ngPass").build()));
    }

    @Test
    void registerRequest_rejects_nameWithAngleBrackets() {
        assertFalse(valid(RegisterRequest.builder()
                .firstName("<script>").lastName("García")
                .email("ana@example.com").password("Str0ngPass").build()));
    }

    @Test
    void registerRequest_rejects_badEmail() {
        assertFalse(valid(RegisterRequest.builder()
                .firstName("Ana").lastName("García")
                .email("not-an-email").password("Str0ngPass").build()));
    }

    @Test
    void registerRequest_rejects_weakPassword() {
        // too short / no upper / no digit
        assertFalse(valid(RegisterRequest.builder()
                .firstName("Ana").lastName("García")
                .email("ana@example.com").password("weak").build()));
    }

    @Test
    void registerRequest_rejects_overlongPassword() {
        assertFalse(valid(RegisterRequest.builder()
                .firstName("Ana").lastName("García")
                .email("ana@example.com").password("A1" + "a".repeat(80)).build()));
    }

    @Test
    void registerRequest_rejects_addressWithAngleBrackets() {
        assertFalse(valid(RegisterRequest.builder()
                .firstName("Ana").lastName("García")
                .email("ana@example.com").password("Str0ngPass")
                .address("Calle <b>1</b>").build()));
    }

    @Test
    void authenticationRequest_rejects_blankPassword() {
        assertFalse(valid(AuthenticationRequest.builder()
                .email("ana@example.com").password("").build()));
    }

    @Test
    void verifyOtpRequest_rejects_badSessionAndOtp() {
        assertFalse(valid(new VerifyOtpRequest("not-a-uuid", "12")));
        assertTrue(valid(new VerifyOtpRequest("11111111-2222-3333-4444-555555555555", "123456")));
    }

    @Test
    void resetPasswordRequest_rejects_weakPassword() {
        assertFalse(valid(ResetPasswordRequest.builder()
                .resetToken("tok").newPassword("weak").build()));
    }

    @Test
    void reviewDto_rejects_ratingOutOfRange() {
        assertFalse(valid(new ReviewDto(null, 2L, null, null, null, "c1", 6.0, "Nice", null)));
        assertTrue(valid(new ReviewDto(null, 2L, null, null, null, "c1", 5.0, "Nice", null)));
    }

    @Test
    void reviewDto_rejects_commentWithAngleBrackets() {
        assertFalse(valid(new ReviewDto(null, 2L, null, null, null, "c1", 5.0, "<img src=x>", null)));
    }
}
