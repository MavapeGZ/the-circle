package com.thecircle.users.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.thecircle.users.dto.KycResponse;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.service.KycService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    @InjectMocks
    private UserController userController;

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
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2});
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
}
