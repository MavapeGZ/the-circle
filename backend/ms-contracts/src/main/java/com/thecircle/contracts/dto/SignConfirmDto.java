package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SignConfirmDto {

    @NotBlank(message = "Session id is required")
    @Pattern(regexp = ValidationPatterns.UUID, message = "Session id " + ValidationPatterns.UUID_MSG)
    private String sessionId;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = ValidationPatterns.OTP, message = "Verification code " + ValidationPatterns.OTP_MSG)
    private String otp;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
