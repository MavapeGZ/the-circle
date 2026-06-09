package com.thecircle.users.dto;

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
    private String resetToken;
    @ToString.Exclude
    private String newPassword;
}
