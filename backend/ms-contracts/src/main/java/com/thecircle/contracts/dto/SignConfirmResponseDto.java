package com.thecircle.contracts.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SignConfirmResponseDto {

    private boolean success;
    private boolean fullySigned;
    private String storedContractId;
    private String downloadUrl;
    private LocalDateTime signedAt;
    private String message;
    // Badges the signer unlocked by completing this signature, so the UI can toast
    // them. Empty unless this signature both activated the contract and the signer
    // is the rewarded party.
    private List<EarnedBadgeDto> earnedBadges = List.of();

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public boolean isFullySigned() { return fullySigned; }
    public void setFullySigned(boolean fullySigned) { this.fullySigned = fullySigned; }
    public String getStoredContractId() { return storedContractId; }
    public void setStoredContractId(String storedContractId) { this.storedContractId = storedContractId; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<EarnedBadgeDto> getEarnedBadges() { return earnedBadges; }
    public void setEarnedBadges(List<EarnedBadgeDto> earnedBadges) {
        this.earnedBadges = earnedBadges != null ? earnedBadges : List.of();
    }
}
