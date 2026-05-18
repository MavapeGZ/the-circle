package com.thecircle.contracts.dto;

import java.time.LocalDateTime;

public class SignatureVerificationDto {

    private String storedContractId;
    private String contractId;
    private String filename;
    private boolean verified;
    private LocalDateTime createdAt;

    public String getStoredContractId() { return storedContractId; }
    public void setStoredContractId(String storedContractId) { this.storedContractId = storedContractId; }
    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
