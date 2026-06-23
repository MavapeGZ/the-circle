package com.thecircle.contracts.service;

public interface OtpDeliveryChannel {
    /**
     * Delivers the signature OTP. {@code locale} is the signer's language tag
     * (ISO 639-1, e.g. "es"/"en") used to localize the email; null/blank falls
     * back to the notifications service default (English).
     */
    void send(String destination, String otp, String signerFullName, String locale);
}
