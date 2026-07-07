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
        @NotBlank(message = "validation.cardNumber.required")
        @Size(max = 25, message = "validation.cardNumber.size")
        @Pattern(regexp = "^[0-9 \\-]{12,25}$", message = "validation.cardNumber.pattern")
        String cardNumber,

        @NotBlank(message = "validation.cardExpiry.required")
        @Pattern(regexp = "^\\d{1,2}/\\d{2,4}$", message = "validation.cardExpiry.pattern")
        String expiry,

        @NotBlank(message = "validation.cvc.required")
        @Pattern(regexp = "^\\d{3,4}$", message = "validation.cvc.pattern")
        String cvc,

        @Size(max = 100, message = "validation.cardholder.size")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.cardholder.noAngle")
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
