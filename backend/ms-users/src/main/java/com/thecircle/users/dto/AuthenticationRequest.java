package com.thecircle.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 320, message = "Email must be at most 320 characters")
    private String email;

    // Kept out of toString so accidental log.info("req={}", req) never leaks the
    // plaintext password. Spring's binding still goes through the setter normally.
    // No complexity rule here on purpose: login must accept whatever the user
    // registered with; only the length cap is enforced (BCrypt 72-byte ceiling).
    @ToString.Exclude
    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Password must be at most 72 characters")
    private String password;
}
