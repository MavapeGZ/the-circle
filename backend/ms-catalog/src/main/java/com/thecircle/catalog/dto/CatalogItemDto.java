package com.thecircle.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CatalogItemDto(
    String id,
    String ownerId,
    String title,
    String description,
    String type, // SALE, RENT, CESSION_TEMPORARY, CESSION_PERMANENT
    BigDecimal price, // Can be null if it's a cession
    LocalDateTime createdAt
) {}
