package com.thecircle.contracts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stored_contracts")
public class StoredContract {

    @Id
    @Column(length = 50)
    private String id; // NanoID

    private String filename;
    private String contractId;

    @Lob
    private byte[] data;

    private LocalDateTime createdAt;
    private String signerEmail;
    private String clientIp;
    private String userAgent;

    public StoredContract() {}

    public StoredContract(String id, String filename, String contractId, byte[] data, LocalDateTime createdAt) {
        this.id = id;
        this.filename = filename;
        this.contractId = contractId;
        this.data = data;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}

