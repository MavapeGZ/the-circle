package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Card payload for the simulated checkout. Bean validation gives an early,
 * structural 400; {@code CardValidator} in the service then does the full Luhn /
 * expiry / CVC check. Nothing here is stored — only the masked last 4 reaches
 * persistence.
 */
public record PaymentRequestDto(
        @NotBlank(message = "Card number is required")
        @Size(max = 25, message = "Card number is too long")
        @Pattern(regexp = "^[0-9 \\-]{12,25}$", message = "Card number must contain only digits, spaces or hyphens")
        String cardNumber,

        @NotBlank(message = "Card expiry is required")
        @Pattern(regexp = "^\\d{1,2}/\\d{2,4}$", message = "Card expiry must be in MM/YY or MM/YYYY format")
        String expiry,

        @NotBlank(message = "CVC is required")
        @Pattern(regexp = "^\\d{3,4}$", message = "CVC must be 3 or 4 digits")
        String cvc,

        @Size(max = 100, message = "Cardholder name must be at most 100 characters")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Cardholder name " + ValidationPatterns.NO_ANGLE_MSG)
        String holderName
) {
    /**
     * Records auto-generate a toString that dumps every component. Mask the
     * card number and CVC so an accidental {@code log.info("req={}", req)}
     * cannot leak full PAN / CVC to log aggregators. Expiry + holder name are
     * not sensitive on their own.
     */
    @Override
    public String toString() {
        return "PaymentRequestDto{cardNumber=***, expiry=" + expiry
                + ", cvc=***, holderName=" + holderName + "}";
    }
}
