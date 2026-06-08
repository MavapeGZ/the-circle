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
) {}
