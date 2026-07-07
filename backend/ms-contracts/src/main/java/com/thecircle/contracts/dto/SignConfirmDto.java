package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SignConfirmDto {

    @NotBlank(message = "validation.sessionId.required")
    @Pattern(regexp = ValidationPatterns.UUID, message = "validation.sessionId.uuid")
    private String sessionId;

    @NotBlank(message = "validation.otp.required")
    @Pattern(regexp = ValidationPatterns.OTP, message = "validation.otp.pattern")
    private String otp;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
