package com.thecircle.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContractDto(
    String id,
    String itemId,
    String ownerId,
    String receiverId,
    ContractType type,
    ContractStatus status,
    BigDecimal guaranteeAmount,
    String conditions,
    LocalDateTime returnDate,
    LocalDateTime createdAt,
    LocalDateTime signedAt
) {}
