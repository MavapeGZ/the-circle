package com.thecircle.contracts.dto;

public class SignRequestResponseDto {

    private String sessionId;
    private String message;
    private String otp; // populated only when signature.otp.expose-in-response=true (dev)

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
