package com.thecircle.contracts.dto;

public class SignConfirmDto {

    private String sessionId;
    private String otp;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
