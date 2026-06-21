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
        @NotBlank(message = "Item id is required")
        @Pattern(regexp = ValidationPatterns.ID, message = "Item id " + ValidationPatterns.ID_MSG)
        String itemId,

        // Owner is usually derived server-side from the article; validate only the
        // shape when present.
        @Pattern(regexp = ValidationPatterns.ID, message = "Owner id " + ValidationPatterns.ID_MSG)
        String ownerId,

        @NotBlank(message = "Receiver id is required")
        @Pattern(regexp = ValidationPatterns.ID, message = "Receiver id " + ValidationPatterns.ID_MSG)
        String receiverId,

        ContractType type,

        // Amounts depend on the contract type (price for SALE, guarantee for RENT)
        // so neither is mandatory here; when present they must be non-negative and
        // sanely scaled.
        @DecimalMin(value = "0.0", message = "Price cannot be negative")
        @Digits(integer = 9, fraction = 2, message = "Price has too many digits")
        BigDecimal price,

        @DecimalMin(value = "0.0", message = "Guarantee amount cannot be negative")
        @Digits(integer = 9, fraction = 2, message = "Guarantee amount has too many digits")
        BigDecimal guaranteeAmount,

        @Size(max = 2000, message = "Conditions must be at most 2000 characters")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Conditions " + ValidationPatterns.NO_ANGLE_MSG)
        String conditions,

        LocalDateTime returnDate
) {
}
