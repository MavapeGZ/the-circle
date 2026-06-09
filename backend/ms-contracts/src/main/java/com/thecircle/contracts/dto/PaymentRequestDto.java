package com.thecircle.contracts.dto;

/**
 * Card payload for the simulated checkout. Validated for structure (Luhn,
 * expiry, CVC) and then discarded — only the masked last 4 reaches storage.
 */
public record PaymentRequestDto(
        String cardNumber,
        String expiry,
        String cvc,
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
