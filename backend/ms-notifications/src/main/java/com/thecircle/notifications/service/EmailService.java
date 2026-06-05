package com.thecircle.notifications.service;

import com.thecircle.notifications.dto.EmailRequestDto;
import com.thecircle.notifications.dto.EmailResponseDto;
import com.thecircle.notifications.model.EmailLog;
import com.thecircle.notifications.model.EmailStatus;
import com.thecircle.notifications.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;
    private final EmailDispatcher emailDispatcher;

    /**
     * Renders the template (fail-fast), persists the email as QUEUED and hands the
     * SMTP send to {@link EmailDispatcher} on a background thread. Returns immediately
     * so callers are not blocked by slow SMTP; delivery completes asynchronously.
     */
    public EmailResponseDto send(EmailRequestDto request) {
        Instant now = Instant.now();
        String htmlBody;
        try {
            htmlBody = renderTemplate(request.getTemplateName(), request.getVariables());
        } catch (RuntimeException e) {
            EmailLog failed = persist(request, EmailStatus.FAILED, "Template render error: " + e.getMessage(), null, now);
            log.error("Template render failed for {} -> {} (emailLogId={})", request.getTemplateName(), request.getTo(), failed.getId(), e);
            throw new EmailDeliveryException("Unexpected error. Please contact our support team.", e);
        }

        EmailLog queued = persist(request, EmailStatus.QUEUED, null, null, now);
        try {
            emailDispatcher.dispatch(queued.getId(), request.getTo(), request.getSubject(), htmlBody);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            queued.setStatus(EmailStatus.FAILED);
            queued.setErrorMessage("Email dispatch rejected (queue full)");
            emailLogRepository.save(queued);
            throw new EmailDeliveryException("We could not queue the email right now. Please try again in a few minutes.", e);
        }

        return EmailResponseDto.builder()
                .id(queued.getId())
                .status(queued.getStatus())
                .sentAt(queued.getSentAt())
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
