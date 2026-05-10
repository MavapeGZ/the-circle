package com.thecircle.users.service;

import com.thecircle.users.model.KycStatus;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void processKyc_whenStorageFails_doesNotPersistPendingStatus() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        KycValidationService kycValidationService = mock(KycValidationService.class);
        JwtService jwtService = mock(JwtService.class);
        KycService kycService = new KycService(userRepository, kycValidationService, jwtService, tempDir.toString());

        Long userId = 2L;
        User user = buildUser(userId, KycStatus.UNVERIFIED);
        MultipartFile front = mock(MultipartFile.class);
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(front.getOriginalFilename()).thenReturn("front.jpg");
        when(front.getInputStream()).thenThrow(new IOException("disk full"));

        try {
            kycService.processKyc(userId, front, back);
        } catch (IOException expected) {
            // expected
        }

        assertEquals(KycStatus.UNVERIFIED, user.getKycStatus());
        verify(userRepository, never()).save(any(User.class));
        verify(kycValidationService, never()).validate(any(Long.class), any(MultipartFile.class), any(MultipartFile.class));
    }

    @Test
    void processKyc_whenValidationRejected_deletesStoredFiles() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        KycValidationService kycValidationService = mock(KycValidationService.class);
        JwtService jwtService = mock(JwtService.class);
        KycService kycService = new KycService(userRepository, kycValidationService, jwtService, tempDir.toString());

        Long userId = 3L;
        User user = buildUser(userId, KycStatus.UNVERIFIED);
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycValidationService.validate(userId, front, back)).thenReturn(false);

        String token = kycService.processKyc(userId, front, back);

        assertNull(token);
        assertEquals(KycStatus.REJECTED, user.getKycStatus());
        Path userDir = tempDir.resolve(String.valueOf(userId));
        assertFalse(Files.exists(userDir.resolve("front.jpg")));
        assertFalse(Files.exists(userDir.resolve("back.jpg")));
    }

    @Test
    void processKyc_whenValidationThrows_deletesStoredFilesAndRestoresStatus() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        KycValidationService kycValidationService = mock(KycValidationService.class);
        JwtService jwtService = mock(JwtService.class);
        KycService kycService = new KycService(userRepository, kycValidationService, jwtService, tempDir.toString());

        Long userId = 4L;
        User user = buildUser(userId, KycStatus.UNVERIFIED);
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycValidationService.validate(userId, front, back)).thenThrow(new RuntimeException("provider exploded"));

        try {
            kycService.processKyc(userId, front, back);
        } catch (RuntimeException expected) {
            // expected
        }

        assertEquals(KycStatus.UNVERIFIED, user.getKycStatus());
        Path userDir = tempDir.resolve(String.valueOf(userId));
        assertFalse(Files.exists(userDir.resolve("front.jpg")));
        assertFalse(Files.exists(userDir.resolve("back.jpg")));
    }

    @Test
    void processKyc_whenFilenameIsTooLong_truncatesStoredName() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        KycValidationService kycValidationService = mock(KycValidationService.class);
        JwtService jwtService = mock(JwtService.class);
        KycService kycService = new KycService(userRepository, kycValidationService, jwtService, tempDir.toString());

        Long userId = 5L;
        User user = buildUser(userId, KycStatus.UNVERIFIED);
        String longFilename = "a".repeat(300) + ".jpg";
        MockMultipartFile front = new MockMultipartFile("front", longFilename, "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycValidationService.validate(userId, front, back)).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(User.class))).thenReturn("jwt-token");

        String token = kycService.processKyc(userId, front, back);

        assertEquals("jwt-token", token);
        Path userDir = tempDir.resolve(String.valueOf(userId));
        String storedFrontName;
        try (var paths = Files.list(userDir)) {
            storedFrontName = paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !"back.jpg".equals(name))
                    .findFirst()
                    .orElseThrow();
        }
        assertTrue(storedFrontName.length() <= 255);
        assertTrue(storedFrontName.endsWith(".jpg"));
    }

    @Test
    void processKyc_whenFilenameContainsPathSeparators_usesFallbackName() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        KycValidationService kycValidationService = mock(KycValidationService.class);
        JwtService jwtService = mock(JwtService.class);
        KycService kycService = new KycService(userRepository, kycValidationService, jwtService, tempDir.toString());

        Long userId = 6L;
        User user = buildUser(userId, KycStatus.UNVERIFIED);
        MockMultipartFile front = new MockMultipartFile("front", "../front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kycValidationService.validate(userId, front, back)).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(User.class))).thenReturn("jwt-token");

        String token = kycService.processKyc(userId, front, back);

        assertEquals("jwt-token", token);
        Path userDir = tempDir.resolve(String.valueOf(userId));
        assertTrue(Files.exists(userDir.resolve("front")));
        assertFalse(Files.exists(tempDir.resolve("front.jpg")));
    }

    private static User buildUser(Long userId, KycStatus status) {
        return User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("password")
                .firstName("Owner")
                .lastName("User")
                .kycStatus(status)
                .role("ROLE_USER")
                .build();
    }
}
