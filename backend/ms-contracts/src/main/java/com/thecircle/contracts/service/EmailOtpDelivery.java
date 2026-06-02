package com.thecircle.contracts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Fallback OTP delivery that talks directly to SMTP. Kept for environments
 * where ms-notifications is unavailable. HttpOtpDelivery is @Primary, so this
 * bean only wins when explicitly selected (or HttpOtpDelivery removed).
 */
@Component
@ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
public class EmailOtpDelivery implements OtpDeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpDelivery.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String subject;
    private final int ttlSeconds;

    @Value("${signature.otp.ttl-seconds:600}")
    private int otpTtlSeconds;
    public EmailOtpDelivery(JavaMailSender mailSender,
                            @Value("${signature.mail.from}") String from,
                            @Value("${signature.mail.subject}") String subject,
                            @Value("${signature.otp.ttl-seconds:600}") int ttlSeconds) {
        this.mailSender = mailSender;
        this.from = from;
        this.subject = subject;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public void send(String destination, String otp, String signerFullName) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(destination);
        msg.setSubject(subject);
        msg.setText(buildBody(otp, signerFullName));
        mailSender.send(msg);
        log.info("OTP email sent to {}", maskEmail(destination));
    }

    private String buildBody(String otp, String signerFullName) {
        String greeting = signerFullName != null && !signerFullName.isEmpty()
                ? "Hello " + signerFullName + ","
                : "Hello,";
        int ttlMinutes = Math.max(1, ttlSeconds / 60);
        return greeting + "\n\n"
                + "Your electronic signature code is:\n\n"
                + "   " + otp + "\n\n"
                + "Enter it in The Circle to sign the contract.\n"
                + "The code expires in " + ttlMinutes + " minute" + (ttlMinutes == 1 ? "" : "s") + ".\n\n"
                + "If you did not request this code, please ignore this message.\n\n"
                + "The Circle - Advanced electronic signature (eIDAS).";
    }

    private String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.charAt(0) + "***" + email.substring(at);
    }
}
