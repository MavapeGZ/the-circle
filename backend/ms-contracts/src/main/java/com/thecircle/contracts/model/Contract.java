package com.thecircle.contracts.model;

import com.thecircle.contracts.dto.ContractStatus;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.dto.GuaranteeStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted business contract (the deal between owner and receiver). The signed
 * PDF artifact is stored separately as a {@link StoredContract}; once a contract
 * is signed its {@code storedContractId} points to that artifact.
 */
@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @Column(length = 50)
    private String id;

    private String itemId;
    private String ownerId;
    private String receiverId;

    @Enumerated(EnumType.STRING)
    private ContractType type;

    @Enumerated(EnumType.STRING)
    private ContractStatus status;

    // Agreed price of the deal (the item's price at signing time). 0 for donations.
    private BigDecimal price;

    private BigDecimal guaranteeAmount;

    @Enumerated(EnumType.STRING)
    private GuaranteeStatus guaranteeStatus = GuaranteeStatus.NONE;

    @Column(length = 2000)
    private String conditions;

    private LocalDateTime returnDate;
    private LocalDateTime createdAt;
    private LocalDateTime signedAt;

    // Per-party signatures. The contract is fully signed (ACTIVE) only once both are set.
    private LocalDateTime receiverSignedAt;
    private LocalDateTime ownerSignedAt;

    // Per-party delivery confirmation, available once the contract is ACTIVE. The
    // owner confirms hand-over (delivered) and the receiver confirms reception
    // (received). When both are set the contract moves to DELIVERED and reviews open.
    private LocalDateTime ownerDeliveredAt;
    private LocalDateTime receiverReceivedAt;

    private String storedContractId;

    public Contract() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
    public ContractType getType() { return type; }
    public void setType(ContractType type) { this.type = type; }
    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getGuaranteeAmount() { return guaranteeAmount; }
    public void setGuaranteeAmount(BigDecimal guaranteeAmount) { this.guaranteeAmount = guaranteeAmount; }
    public GuaranteeStatus getGuaranteeStatus() { return guaranteeStatus; }
    public void setGuaranteeStatus(GuaranteeStatus guaranteeStatus) { this.guaranteeStatus = guaranteeStatus; }
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public LocalDateTime getReceiverSignedAt() { return receiverSignedAt; }
    public void setReceiverSignedAt(LocalDateTime receiverSignedAt) { this.receiverSignedAt = receiverSignedAt; }
    public LocalDateTime getOwnerSignedAt() { return ownerSignedAt; }
    public void setOwnerSignedAt(LocalDateTime ownerSignedAt) { this.ownerSignedAt = ownerSignedAt; }
    public LocalDateTime getOwnerDeliveredAt() { return ownerDeliveredAt; }
    public void setOwnerDeliveredAt(LocalDateTime ownerDeliveredAt) { this.ownerDeliveredAt = ownerDeliveredAt; }
    public LocalDateTime getReceiverReceivedAt() { return receiverReceivedAt; }
    public void setReceiverReceivedAt(LocalDateTime receiverReceivedAt) { this.receiverReceivedAt = receiverReceivedAt; }
    public String getStoredContractId() { return storedContractId; }
    public void setStoredContractId(String storedContractId) { this.storedContractId = storedContractId; }
}
