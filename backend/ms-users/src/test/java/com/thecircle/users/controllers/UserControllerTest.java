package com.thecircle.users.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.thecircle.users.dto.KycResponse;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.service.CatalogClient;
import com.thecircle.users.service.ContractsClient;
import com.thecircle.users.service.DeviceCookieService;
import com.thecircle.users.service.RefreshTokenService;
import com.thecircle.users.service.IbanCipher;
import com.thecircle.users.service.KycService;
import com.thecircle.users.service.GamificationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private KycService kycService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GamificationClient gamificationClient;

    @Mock
    private DeviceCookieService deviceCookieService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private IbanCipher ibanCipher;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private ContractsClient contractsClient;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private com.thecircle.users.service.ReviewService reviewService;

    @InjectMocks
    private UserController userController;

    // Minimal but real JPEG magic bytes (SOI + APP0) so upload validation passes.
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private static Authentication ownerAuth() {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("owner@example.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        return new UsernamePasswordAuthenticationToken(principal, "password", principal.getAuthorities());
    }

    @Test
    void uploadIdentity_whenFileTypeDisguised_returnsBadRequest() throws Exception {
        Long userId = 1L;
        // .png extension + declared png, but the bytes are not a real PNG.
        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", new byte[]{1, 2, 3, 4});
        MockMultipartFile back = new MockMultipartFile("back", "back.png", "image/png", new byte[]{1, 2, 3, 4});

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(buildUser(userId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));

        ResponseEntity<KycResponse> response = userController.uploadIdentity(userId, front, back, ownerAuth());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
    }

    @Test
    void uploadIdentity_whenFileEmpty_returnsBadRequest() {
        Long userId = 1L;
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[0]);
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", JPEG_BYTES);

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(buildUser(userId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));

        ResponseEntity<KycResponse> response = userController.uploadIdentity(userId, front, back, ownerAuth());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private static User buildUser(Long userId) {
        return User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("password")
                .firstName("Owner")
                .lastName("User")
                .kycStatus(KycStatus.UNVERIFIED)
                .role("ROLE_USER")
                .build();
    }

    @Test
    void uploadIdentity_whenKycProcessingFails_returnsGenericInternalServerError() throws Exception {
        Long userId = 1L;
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", JPEG_BYTES);
        Logger logger = (Logger) LoggerFactory.getLogger(UserController.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("owner@example.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "password",
                principal.getAuthorities()
        );

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(buildUser(userId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(kycService.processKyc(userId, front, back)).thenThrow(new RuntimeException("provider exploded"));

        try {
            ResponseEntity<KycResponse> response = userController.uploadIdentity(userId, front, back, authentication);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().success());
            assertEquals("Verification failed. Please try again later.", response.getBody().message());
            assertFalse(response.getBody().message().contains("provider exploded"));
            assertNull(response.getBody().jwt());

            assertEquals(1, listAppender.list.size());
            ILoggingEvent loggingEvent = listAppender.list.getFirst();
            assertEquals(Level.ERROR, loggingEvent.getLevel());
            assertEquals("KYC verification failed for user {}", loggingEvent.getMessage());
            assertEquals(userId, loggingEvent.getArgumentArray()[0]);
            assertNotNull(loggingEvent.getThrowableProxy());
            assertTrue(loggingEvent.getThrowableProxy().getMessage().contains("provider exploded"));
        } finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    void deleteAccount_requestsContractCleanupForOwnedOpenContracts() {
        Long userId = 1L;
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("owner@example.com")
                .password("password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "password",
                principal.getAuthorities()
        );

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(buildUser(userId)));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("encoded");

        ResponseEntity<Void> response = userController.deleteAccount(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        org.mockito.Mockito.verify(deviceCookieService).revokeAllDevices(userId);
        org.mockito.Mockito.verify(refreshTokenService).revokeAllForUser(userId);
        org.mockito.Mockito.verify(catalogClient).removeUserArticles(userId);
        org.mockito.Mockito.verify(contractsClient).removeOwnedOpenContracts(userId);
    }
}
