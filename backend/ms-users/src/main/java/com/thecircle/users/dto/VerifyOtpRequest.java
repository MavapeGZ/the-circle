package com.thecircle.users.dto;

import com.thecircle.users.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Session id is required")
    @Pattern(regexp = ValidationPatterns.UUID, message = "Session id " + ValidationPatterns.UUID_MSG)
    private String sessionId;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = ValidationPatterns.OTP, message = "Verification code " + ValidationPatterns.OTP_MSG)
    private String otp;
}
