package com.thecircle.contracts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signature_sessions")
public class SignatureSession {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "signer_key", nullable = false, length = 64)
    private String signerKey;

    @Column(name = "signer_email", nullable = false)
    private String signerEmail;

    @Column(name = "signer_full_name")
    private String signerFullName;

    @Column(name = "otp_hash", nullable = false, length = 200)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SignatureSessionStatus status;

    @Lob
    @Column(name = "payload_pdf")
    private byte[] payloadPdf;

    @Lob
    @Column(name = "contract_json")
    private String contractJson;

    @Lob
    @Column(name = "visual_options_json")
    private String visualOptionsJson;

    @Column(name = "pre_hash", length = 128)
    private String preHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SignatureSession() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public String getSignerKey() { return signerKey; }
    public void setSignerKey(String signerKey) { this.signerKey = signerKey; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public String getSignerFullName() { return signerFullName; }
    public void setSignerFullName(String signerFullName) { this.signerFullName = signerFullName; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public SignatureSessionStatus getStatus() { return status; }
    public void setStatus(SignatureSessionStatus status) { this.status = status; }
    public byte[] getPayloadPdf() { return payloadPdf; }
    public void setPayloadPdf(byte[] payloadPdf) { this.payloadPdf = payloadPdf; }
    public String getContractJson() { return contractJson; }
    public void setContractJson(String contractJson) { this.contractJson = contractJson; }
    public String getVisualOptionsJson() { return visualOptionsJson; }
    public void setVisualOptionsJson(String visualOptionsJson) { this.visualOptionsJson = visualOptionsJson; }
    public String getPreHash() { return preHash; }
    public void setPreHash(String preHash) { this.preHash = preHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
