package com.thecircle.users.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class MockKycProviderTest {

    @Test
    void validate_variousInputs_returnExpectedResults() {
        MockMultipartFile validFront = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[1024]);
        MockMultipartFile validBack = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[2048]);

        byte[] big = new byte[(int) (5 * 1024 * 1024) + 10];
        MockMultipartFile tooLargeFront = new MockMultipartFile("front", "front.jpg", "image/jpeg", big);
        MockMultipartFile tooLargeBack = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[10]);

        MockMultipartFile missingFront = null;
        MockMultipartFile missingBack = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[10]);

        MockMultipartFile badExtensionFront = new MockMultipartFile("front", "front.exe", "application/octet-stream", new byte[10]);
        MockMultipartFile badExtensionBack = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[10]);

        CompletableFuture<Boolean> validResult = CompletableFuture.supplyAsync(() -> {
            try {
                return new MockKycProvider().validate(1L, validFront, validBack);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Boolean> tooLargeResult = CompletableFuture.supplyAsync(() -> {
            try {
                return new MockKycProvider().validate(2L, tooLargeFront, tooLargeBack);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Boolean> missingFileResult = CompletableFuture.supplyAsync(() -> {
            try {
                return new MockKycProvider().validate(3L, missingFront, missingBack);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Boolean> badExtensionResult = CompletableFuture.supplyAsync(() -> {
            try {
                return new MockKycProvider().validate(4L, badExtensionFront, badExtensionBack);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(validResult, tooLargeResult, missingFileResult, badExtensionResult).join();

        assertAll(
                () -> assertTrue(validResult.join(), "Valid JPEG files should be accepted by the mock provider"),
                () -> assertFalse(tooLargeResult.join(), "Files larger than 5MB should be rejected"),
                () -> assertFalse(missingFileResult.join(), "Missing front or back file must be rejected"),
                () -> assertFalse(badExtensionResult.join(), "Unsupported file extensions should be rejected")
        );
    }
}

