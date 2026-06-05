package com.thecircle.notifications.service;

import com.thecircle.notifications.model.EmailLog;
import com.thecircle.notifications.model.EmailStatus;
import com.thecircle.notifications.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Performs the actual SMTP send off the request thread. Lives in its own bean
 * so the @Async proxy applies (a self-invoked @Async method would run inline).
 * Never propagates exceptions: failures are recorded on the EmailLog row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDispatcher {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;

    @Value("${notifications.mail.from:no-reply@thecircle.local}")
    private String fromAddress;

    @Async("emailExecutor")
    public void dispatch(Long logId, String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            markSent(logId);
        } catch (Exception e) {
            log.error("SMTP send failed for {} (emailLogId={})", to, logId, e);
            markFailed(logId, e.getMessage());
        }
    }

    private void markSent(Long logId) {
        emailLogRepository.findById(logId).ifPresent(entry -> {
            entry.setStatus(EmailStatus.SENT);
            entry.setSentAt(Instant.now());
            entry.setErrorMessage(null);
            emailLogRepository.save(entry);
        });
    }

    private void markFailed(Long logId, String error) {
        emailLogRepository.findById(logId).ifPresent(entry -> {
            entry.setStatus(EmailStatus.FAILED);
            entry.setSentAt(null);
            entry.setErrorMessage(truncate(error, 1000));
            emailLogRepository.save(entry);
        });
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
