package com.thecircle.users.service;

import com.thecircle.users.model.KycStatus;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void processKyc_usesConfiguredUploadDirectory() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        KycValidationService kycValidationService = mock(KycValidationService.class);
        JwtService jwtService = mock(JwtService.class);
        KycService kycService = new KycService(userRepository, kycValidationService, jwtService, tempDir.toString());

        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("password")
                .firstName("Owner")
                .lastName("User")
                .kycStatus(KycStatus.UNVERIFIED)
                .role("ROLE_USER")
                .build();
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycValidationService.validate(userId, front, back)).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(User.class))).thenReturn("jwt-token");

        String token = kycService.processKyc(userId, front, back);

        assertEquals("jwt-token", token);
        Path userDir = tempDir.resolve(String.valueOf(userId));
        assertTrue(Files.isDirectory(userDir));
        assertTrue(Files.exists(userDir.resolve("front.jpg")));
        assertTrue(Files.exists(userDir.resolve("back.jpg")));
        assertNotNull(user.getKycStatus());
        assertEquals(KycStatus.VERIFIED, user.getKycStatus());
    }
}
