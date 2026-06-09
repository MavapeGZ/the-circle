package com.thecircle.contracts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = OtpDeliveryChannel.class, ignored = LogOtpDelivery.class)
@ConditionalOnProperty(name = "spring.mail.host", havingValue = "", matchIfMissing = true)
public class LogOtpDelivery implements OtpDeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(LogOtpDelivery.class);

    @Override
    public void send(String destination, String otp, String signerFullName) {
        log.warn("[DEV OTP DELIVERY] destination={} signer={} otp={}", destination, signerFullName, otp);
    }
}
