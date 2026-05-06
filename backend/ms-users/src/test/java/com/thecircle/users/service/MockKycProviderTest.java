package com.thecircle.users.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

public class MockKycProviderTest {

    private MockKycProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockKycProvider();
    }

    @Test
    void validate_withValidFiles_returnsTrue() throws Exception {
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[1024]);
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[2048]);

        boolean ok = provider.validate(1L, front, back);
        assertTrue(ok, "Valid JPEG files should be accepted by the mock provider");
    }

    @Test
    void validate_withTooLargeFile_returnsFalse() throws Exception {
        // create a payload bigger than 5MB
        byte[] big = new byte[(int)(5 * 1024 * 1024) + 10];
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", big);
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[10]);

        boolean ok = provider.validate(2L, front, back);
        assertFalse(ok, "Files larger than 5MB should be rejected");
    }

    @Test
    void validate_withMissingFile_returnsFalse() throws Exception {
        MockMultipartFile front = null;
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[10]);

        boolean ok = provider.validate(3L, front, back);
        assertFalse(ok, "Missing front or back file must be rejected");
    }

    @Test
    void validate_withBadExtension_returnsFalse() throws Exception {
        MockMultipartFile front = new MockMultipartFile("front", "front.exe", "application/octet-stream", new byte[10]);
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[10]);

        boolean ok = provider.validate(4L, front, back);
        assertFalse(ok, "Unsupported file extensions should be rejected");
    }
}

