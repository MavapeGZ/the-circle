package com.thecircle.contracts.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ContractDto {

    @JsonProperty("id")
    @JsonAlias({"contractId"})
    private String contractId;

    @Size(max = 255, message = "Property address must be at most 255 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Property address " + ValidationPatterns.NO_ANGLE_MSG)
    private String propertyAddress;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private BigDecimal price;
    private List<@Size(max = 2000, message = "Clause is too long")
            @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Clause " + ValidationPatterns.NO_ANGLE_MSG) String> clauses;
    @Valid
    private SignerDto primarySigner;
    @Valid
    private SignerDto secondarySigner;

    private String itemId;
    private String ownerId;
    private String receiverId;
    private ContractType type;
    private ContractStatus status;
    private BigDecimal guaranteeAmount;
    private GuaranteeStatus guaranteeStatus;
    @Size(max = 2000, message = "Conditions must be at most 2000 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Conditions " + ValidationPatterns.NO_ANGLE_MSG)
    private String conditions;
    private LocalDateTime returnDate;
    private LocalDateTime createdAt;
    private LocalDateTime signedAt;
    private LocalDateTime receiverSignedAt;
    private LocalDateTime ownerSignedAt;
    private LocalDateTime ownerDeliveredAt;
    private LocalDateTime receiverReceivedAt;
    private String storedContractId;

    public ContractDto() {}

    public ContractDto(String contractId, String itemId, String ownerId, String receiverId,
                       ContractType type, ContractStatus status, BigDecimal guaranteeAmount,
                       String conditions, LocalDateTime returnDate, LocalDateTime createdAt, LocalDateTime signedAt) {
        this.contractId = contractId;
        this.itemId = itemId;
        this.ownerId = ownerId;
        this.receiverId = receiverId;
        this.type = type;
        this.status = status;
        this.guaranteeAmount = guaranteeAmount;
        this.conditions = conditions;
        this.returnDate = returnDate;
        this.createdAt = createdAt;
        this.signedAt = signedAt;
    }

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(BigDecimal monthlyRent) { this.monthlyRent = monthlyRent; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public List<String> getClauses() { return clauses; }
    public void setClauses(List<String> clauses) { this.clauses = clauses; }
    public SignerDto getPrimarySigner() { return primarySigner; }
    public void setPrimarySigner(SignerDto primarySigner) { this.primarySigner = primarySigner; }
    public SignerDto getSecondarySigner() { return secondarySigner; }
    public void setSecondarySigner(SignerDto secondarySigner) { this.secondarySigner = secondarySigner; }

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
