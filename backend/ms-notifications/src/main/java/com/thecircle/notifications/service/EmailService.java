package com.thecircle.notifications.service;

import com.thecircle.notifications.dto.EmailRequestDto;
import com.thecircle.notifications.dto.EmailResponseDto;
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
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${notifications.mail.from:no-reply@thecircle.local}")
    private String fromAddress;

    public EmailResponseDto send(EmailRequestDto request) {
        Instant now = Instant.now();
        String htmlBody;
        try {
            htmlBody = renderTemplate(request.getTemplateName(), request.getVariables());
        } catch (RuntimeException e) {
            EmailLog failed = persist(request, EmailStatus.FAILED, "Template render error: " + e.getMessage(), null, now);
            log.error("Template render failed for {} -> {}", request.getTemplateName(), request.getTo(), e);
            throw new EmailDeliveryException("Failed to render template: " + request.getTemplateName(), e);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            EmailLog failed = persist(request, EmailStatus.FAILED, e.getMessage(), null, now);
            log.error("SMTP send failed for {}", request.getTo(), e);
            throw new EmailDeliveryException("Failed to deliver email to " + request.getTo(), e);
        }

        Instant sentAt = Instant.now();
        EmailLog saved = persist(request, EmailStatus.SENT, null, sentAt, now);
        return EmailResponseDto.builder()
                .id(saved.getId())
                .status(saved.getStatus())
                .sentAt(saved.getSentAt())
                .build();
    }

    private String renderTemplate(String templateName, Map<String, Object> variables) {
        Context ctx = new Context();
        if (variables != null) {
            ctx.setVariables(variables);
        }
        return templateEngine.process("email/" + templateName, ctx);
    }

    private EmailLog persist(EmailRequestDto request, EmailStatus status, String errorMessage, Instant sentAt, Instant createdAt) {
        EmailLog entry = EmailLog.builder()
                .toAddress(request.getTo())
                .subject(request.getSubject())
                .templateName(request.getTemplateName())
                .status(status)
                .errorMessage(truncate(errorMessage, 1000))
                .sentAt(sentAt)
                .createdAt(createdAt)
                .build();
        return emailLogRepository.save(entry);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
