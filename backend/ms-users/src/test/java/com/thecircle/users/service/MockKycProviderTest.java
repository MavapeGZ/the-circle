package com.thecircle.users.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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

        MockKycProvider provider = new MockKycProvider();
        boolean validResult = provider.validate(1L, validFront, validBack);
        boolean tooLargeResult = provider.validate(2L, tooLargeFront, tooLargeBack);
        boolean missingFileResult = provider.validate(3L, missingFront, missingBack);
        boolean badExtensionResult = provider.validate(4L, badExtensionFront, badExtensionBack);

        assertAll(
                () -> assertTrue(validResult, "Valid JPEG files should be accepted by the mock provider"),
                () -> assertFalse(tooLargeResult, "Files larger than 5MB should be rejected"),
                () -> assertFalse(missingFileResult, "Missing front or back file must be rejected"),
                () -> assertFalse(badExtensionResult, "Unsupported file extensions should be rejected")
        );
    }
}
