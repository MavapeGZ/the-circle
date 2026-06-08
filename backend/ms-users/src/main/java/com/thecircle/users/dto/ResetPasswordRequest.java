package com.thecircle.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    // Short-lived single-use token issued by /auth/verify-reset-otp.
    private String resetToken;
    private String newPassword;
}
