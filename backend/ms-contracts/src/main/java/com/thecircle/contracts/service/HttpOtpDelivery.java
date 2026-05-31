package com.thecircle.contracts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends OTP emails by delegating to ms-notifications over HTTP instead of
 * talking to SMTP directly. Selected by default; EmailOtpDelivery remains as
 * a fallback when this channel is explicitly disabled.
 */
@Component
@Primary
public class HttpOtpDelivery implements OtpDeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(HttpOtpDelivery.class);
    private static final String EMAIL_PATH = "/api/notifications/email";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";
    private static final String OTP_TEMPLATE_NAME = "otp";

    private final RestTemplate restTemplate;
    private final String notificationsBaseUrl;
    private final String internalApiKey;
    private final String subject;
    private final int ttlMinutes;

    public HttpOtpDelivery(RestTemplate notificationsRestTemplate,
                           @Value("${notifications.url:http://localhost:8085}") String notificationsBaseUrl,
                           @Value("${notifications.api-key:}") String internalApiKey,
                           @Value("${signature.mail.subject:The Circle - Codigo de firma electronica}") String subject,
                           @Value("${signature.otp.ttl-seconds:600}") int ttlSeconds) {
        this.restTemplate = notificationsRestTemplate;
        this.notificationsBaseUrl = stripTrailingSlash(notificationsBaseUrl);
        this.internalApiKey = internalApiKey;
        this.subject = subject;
        this.ttlMinutes = Math.max(1, ttlSeconds / 60);
    }

    @Override
    public void send(String destination, String otp, String signerFullName) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (signerFullName != null && !signerFullName.isBlank()) {
            variables.put("recipientName", signerFullName);
        }
        variables.put("otpCode", otp);
        variables.put("ttlMinutes", ttlMinutes);

        Map<String, Object> body = new HashMap<>();
        body.put("to", destination);
        body.put("subject", subject);
        body.put("templateName", OTP_TEMPLATE_NAME);
        body.put("variables", variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set(INTERNAL_KEY_HEADER, internalApiKey);
        }

        String url = notificationsBaseUrl + EMAIL_PATH;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("ms-notifications returned " + response.getStatusCode());
            }
            log.info("OTP email dispatched via ms-notifications to {}", maskEmail(destination));
        } catch (RestClientException ex) {
            log.error("Failed to dispatch OTP email via ms-notifications ({} -> {})", url, maskEmail(destination), ex);
            throw ex;
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.charAt(0) + "***" + email.substring(at);
    }
}
