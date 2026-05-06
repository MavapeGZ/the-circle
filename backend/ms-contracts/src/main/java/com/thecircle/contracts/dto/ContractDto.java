package com.thecircle.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ContractDto {
    // Existing fields for PDF generation
    private String contractId;
    private String propertyAddress;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private List<String> clauses;
    private SignerDto primarySigner;
    private SignerDto secondarySigner;

    // Additional fields used by existing controllers
    private String itemId;
    private String ownerId;
    private String receiverId;
    private ContractType type;
    private ContractStatus status;
    private BigDecimal guaranteeAmount;
    private String conditions;
    private LocalDateTime returnDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // No-arg constructor
    public ContractDto() {}

    // Constructor matching controller usages
    public ContractDto(String contractId, String itemId, String ownerId, String receiverId,
                       ContractType type, ContractStatus status, BigDecimal guaranteeAmount,
                       String conditions, LocalDateTime returnDate, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
        this.updatedAt = updatedAt;
    }

    // getters and setters
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
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
