package com.thecircle.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDto(
        String id,
        String contractId,
        String payerUserId,
        String payeeUserId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String cardLast4,
        String cardBrand,
        String payoutIbanLast4,
        LocalDateTime simulatedAt,
        LocalDateTime escrowExpiresAt,
        LocalDateTime releasedAt,
        LocalDateTime refundedAt,
        String failureReason
) {}
