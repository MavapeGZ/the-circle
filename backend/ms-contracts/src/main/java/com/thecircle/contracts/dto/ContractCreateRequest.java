package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ContractCreateRequest(
        @NotBlank(message = "validation.itemId.required")
        @Pattern(regexp = ValidationPatterns.ID, message = "validation.itemId.pattern")
        String itemId,

        // Owner is usually derived server-side from the article; validate only the
        // shape when present.
        @Pattern(regexp = ValidationPatterns.ID, message = "validation.ownerId.pattern")
        String ownerId,

        @NotBlank(message = "validation.receiverId.required")
        @Pattern(regexp = ValidationPatterns.ID, message = "validation.receiverId.pattern")
        String receiverId,

        ContractType type,

        // Amounts depend on the contract type (price for SALE, guarantee for RENT)
        // so neither is mandatory here; when present they must be non-negative and
        // sanely scaled.
        @DecimalMin(value = "0.0", message = "validation.price.negative")
        @Digits(integer = 9, fraction = 2, message = "validation.price.digits")
        BigDecimal price,

        @DecimalMin(value = "0.0", message = "validation.guarantee.negative")
        @Digits(integer = 9, fraction = 2, message = "validation.guarantee.digits")
        BigDecimal guaranteeAmount,

        @Size(max = 2000, message = "validation.conditions.size")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.conditions.noAngle")
        String conditions,

        LocalDateTime returnDate
) {
}
