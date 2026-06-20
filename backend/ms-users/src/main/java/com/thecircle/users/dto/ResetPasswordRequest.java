package com.thecircle.users.dto;

import com.thecircle.users.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    // Short-lived single-use token issued by /auth/verify-reset-otp. Treated
    // as a secret: leaking it lets anyone (within the IP-binding window)
    // complete the reset.
    @ToString.Exclude
    @NotBlank(message = "Reset token is required")
    private String resetToken;

    // Same strength rule as registration: a reset must not be a back-door to a
    // weaker password than signup allows.
    @ToString.Exclude
    @NotBlank(message = "New password is required")
    @Pattern(regexp = ValidationPatterns.PASSWORD, message = "Password " + ValidationPatterns.PASSWORD_MSG)
    private String newPassword;
}
