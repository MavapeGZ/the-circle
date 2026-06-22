package com.thecircle.users.security;

import com.thecircle.users.controllers.UserController;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.service.AvatarService;
import com.thecircle.users.service.CatalogClient;
import com.thecircle.users.service.ContractsClient;
import com.thecircle.users.service.DeviceCookieService;
import com.thecircle.users.service.GamificationClient;
import com.thecircle.users.service.IbanCipher;
import com.thecircle.users.service.KycService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the SecurityConfig fix: a controller that throws
 * {@link org.springframework.web.server.ResponseStatusException} must surface the
 * real 4xx, not be masked as 401 by the internal /error re-dispatch in a
 * stateless filter chain. Here PATCH /me with an invalid zone yields a 400.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityErrorDispatchTest {

    @Autowired
    private MockMvc mockMvc;

    // SecurityConfig + JwtAuthenticationFilter collaborators.
    @MockBean
    private AuthenticationProvider authenticationProvider;
    @MockBean
    private com.thecircle.users.security.JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService;

    // UserController collaborators.
    @MockBean
    private KycService kycService;
    @MockBean
    private AvatarService avatarService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private DeviceCookieService deviceCookieService;
    @MockBean
    private com.thecircle.users.service.RefreshTokenService refreshTokenService;
    @MockBean
    private IbanCipher ibanCipher;
    @MockBean
    private CatalogClient catalogClient;
    @MockBean
    private ContractsClient contractsClient;
    @MockBean
    private GamificationClient gamificationClient;

    @Test
    @WithMockUser(username = "owner@example.com")
    void invalidZone_returns400_notMaskedAs401() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(
                User.builder()
                        .id(1L)
                        .email("owner@example.com")
                        .firstName("Owner")
                        .lastName("User")
                        .kycStatus(KycStatus.UNVERIFIED)
                        .role("ROLE_USER")
                        .build()));

        mockMvc.perform(patch("/api/users/me")
                        .contentType("application/json")
                        .content("{\"zone\":\"NARNIA\"}"))
                .andExpect(status().isBadRequest());
    }
}
