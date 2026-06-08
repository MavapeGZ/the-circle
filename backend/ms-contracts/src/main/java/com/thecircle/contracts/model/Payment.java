package com.thecircle.contracts.model;

import com.thecircle.contracts.dto.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Simulated payment for a contract. Money is never moved — the row exists to
 * model the escrow lifecycle (paid by buyer, released to seller on dual
 * signature, refunded on counterparty timeout) and to give the UX something to
 * render as a receipt.
 *
 * <p>Only masked card details are stored ({@code cardLast4}, {@code cardBrand}).
 * Full PAN, CVC, expiry are validated in-memory and discarded.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "contract_id", length = 50, nullable = false)
    private String contractId;

    @Column(name = "payer_user_id", length = 50, nullable = false)
    private String payerUserId;

    @Column(name = "payee_user_id", length = 50, nullable = false)
    private String payeeUserId;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private PaymentStatus status;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_brand", length = 16)
    private String cardBrand;

    @Column(name = "payout_iban_last4", length = 4)
    private String payoutIbanLast4;

    @Column(name = "simulated_at", nullable = false)
    private LocalDateTime simulatedAt;

    @Column(name = "escrow_expires_at")
    private LocalDateTime escrowExpiresAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    public Payment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public String getPayerUserId() { return payerUserId; }
    public void setPayerUserId(String payerUserId) { this.payerUserId = payerUserId; }
    public String getPayeeUserId() { return payeeUserId; }
    public void setPayeeUserId(String payeeUserId) { this.payeeUserId = payeeUserId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getPayoutIbanLast4() { return payoutIbanLast4; }
    public void setPayoutIbanLast4(String payoutIbanLast4) { this.payoutIbanLast4 = payoutIbanLast4; }
    public LocalDateTime getSimulatedAt() { return simulatedAt; }
    public void setSimulatedAt(LocalDateTime simulatedAt) { this.simulatedAt = simulatedAt; }
    public LocalDateTime getEscrowExpiresAt() { return escrowExpiresAt; }
    public void setEscrowExpiresAt(LocalDateTime escrowExpiresAt) { this.escrowExpiresAt = escrowExpiresAt; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
