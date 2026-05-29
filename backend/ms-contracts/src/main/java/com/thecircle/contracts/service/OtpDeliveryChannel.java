package com.thecircle.contracts.service;

public interface OtpDeliveryChannel {
    void send(String destination, String otp, String signerFullName);
}
