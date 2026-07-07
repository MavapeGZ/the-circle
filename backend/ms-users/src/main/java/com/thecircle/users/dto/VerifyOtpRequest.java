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

    @NotBlank(message = "validation.sessionId.required")
    @Pattern(regexp = ValidationPatterns.UUID, message = "validation.sessionId.uuid")
    private String sessionId;

    @NotBlank(message = "validation.otp.required")
    @Pattern(regexp = ValidationPatterns.OTP, message = "validation.otp.pattern")
    private String otp;
}
