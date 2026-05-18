package com.thecircle.contracts.dto;

import java.time.LocalDateTime;

public class SignConfirmResponseDto {

    private boolean success;
    private String storedContractId;
    private String downloadUrl;
    private LocalDateTime signedAt;
    private String message;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getStoredContractId() { return storedContractId; }
    public void setStoredContractId(String storedContractId) { this.storedContractId = storedContractId; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
