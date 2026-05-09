package com.thecircle.users.controllers;

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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private KycService kycService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserController userController;

    @Test
    void uploadIdentity_whenKycProcessingFails_returnsGenericInternalServerError() throws Exception {
        Long userId = 1L;
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2});
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

        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(User.builder()
                .id(userId)
                .email("owner@example.com")
                .password("password")
                .firstName("Owner")
                .lastName("User")
                .kycStatus(KycStatus.UNVERIFIED)
                .role("ROLE_USER")
                .build()));
        when(kycService.processKyc(userId, front, back)).thenThrow(new RuntimeException("provider exploded"));

        ResponseEntity<KycResponse> response = userController.uploadIdentity(userId, front, back, authentication);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Verification failed. Please try again later.", response.getBody().message());
        assertFalse(response.getBody().message().contains("provider exploded"));
        assertNull(response.getBody().jwt());
    }
}
