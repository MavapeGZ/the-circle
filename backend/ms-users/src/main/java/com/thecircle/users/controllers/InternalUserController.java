package com.thecircle.users.controllers;

import com.thecircle.users.dto.UserIdentityDto;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service-to-service identity endpoint. Returns PII (address, ID number) needed
 * to render signed contract PDFs. Lives under /internal/** which the API gateway
 * does NOT route, so it is not reachable from the public internet, and is further
 * guarded by a shared internal API key.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final UserRepository userRepository;

    @Value("${users.internal.api-key:}")
    private String internalApiKey;

    @GetMapping("/{userId}/identity")
    public ResponseEntity<UserIdentityDto> identity(@PathVariable Long userId, HttpServletRequest request) {
        assertInternalCaller(request);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(new UserIdentityDto(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getAddress(), u.getIdNumber()));
    }

    private void assertInternalCaller(HttpServletRequest request) {
        // Blank key disables the guard (local dev), mirroring ms-notifications.
        if (internalApiKey == null || internalApiKey.isBlank()) {
            return;
        }
        if (!internalApiKey.equals(request.getHeader(INTERNAL_KEY_HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to perform this action.");
        }
    }
}
