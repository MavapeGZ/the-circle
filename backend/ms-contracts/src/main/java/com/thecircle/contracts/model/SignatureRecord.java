package com.thecircle.contracts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signature_records")
public class SignatureRecord {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "stored_contract_id", nullable = false, length = 50)
    private String storedContractId;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "signer_key", nullable = false, length = 64)
    private String signerKey;

    @Column(name = "signer_full_name")
    private String signerFullName;

    @Column(name = "signer_id_number", length = 64)
    private String signerIdNumber;

    @Column(name = "signer_email")
    private String signerEmail;

    @Column(name = "signed_at_utc", nullable = false)
    private LocalDateTime signedAtUtc;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "otp_session_id", nullable = false, length = 50)
    private String otpSessionId;

    @Column(name = "pre_hash", nullable = false, length = 128)
    private String preHash;

    @Column(name = "post_hash", nullable = false, length = 128)
    private String postHash;

    @Column(nullable = false, length = 32)
    private String algorithm;

    public SignatureRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStoredContractId() { return storedContractId; }
    public void setStoredContractId(String storedContractId) { this.storedContractId = storedContractId; }
    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public String getSignerKey() { return signerKey; }
    public void setSignerKey(String signerKey) { this.signerKey = signerKey; }
    public String getSignerFullName() { return signerFullName; }
    public void setSignerFullName(String signerFullName) { this.signerFullName = signerFullName; }
    public String getSignerIdNumber() { return signerIdNumber; }
    public void setSignerIdNumber(String signerIdNumber) { this.signerIdNumber = signerIdNumber; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public LocalDateTime getSignedAtUtc() { return signedAtUtc; }
    public void setSignedAtUtc(LocalDateTime signedAtUtc) { this.signedAtUtc = signedAtUtc; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getOtpSessionId() { return otpSessionId; }
    public void setOtpSessionId(String otpSessionId) { this.otpSessionId = otpSessionId; }
    public String getPreHash() { return preHash; }
    public void setPreHash(String preHash) { this.preHash = preHash; }
    public String getPostHash() { return postHash; }
    public void setPostHash(String postHash) { this.postHash = postHash; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
}
