package com.thecircle.notifications.service;

import com.thecircle.notifications.dto.EmailRequestDto;
import com.thecircle.notifications.dto.EmailResponseDto;
import com.thecircle.notifications.model.EmailLog;
import com.thecircle.notifications.model.EmailStatus;
import com.thecircle.notifications.repository.EmailLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private SpringTemplateEngine templateEngine;
    private EmailLogRepository repository;
    private EmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        templateEngine = mock(SpringTemplateEngine.class);
        repository = mock(EmailLogRepository.class);
        service = new EmailService(mailSender, templateEngine, repository);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@test.local");

        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        when(templateEngine.process(eq("email/otp"), any())).thenReturn("<p>123456</p>");
        when(repository.save(any(EmailLog.class))).thenAnswer(inv -> {
            EmailLog log = inv.getArgument(0);
            log.setId(42L);
            return log;
        });
    }

    @Test
    void send_success_persistsSentAndReturnsResponse() {
        EmailRequestDto req = EmailRequestDto.builder()
                .to("user@test.local")
                .subject("Codigo")
                .templateName("otp")
                .variables(Map.of("otpCode", "123456"))
                .build();

        EmailResponseDto response = service.send(req);

        assertThat(response.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(response.getSentAt()).isNotNull();
        assertThat(response.getId()).isEqualTo(42L);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(saved.getToAddress()).isEqualTo("user@test.local");
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void send_smtpFailure_persistsFailedAndThrows() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        EmailRequestDto req = EmailRequestDto.builder()
                .to("user@test.local")
                .subject("Codigo")
                .templateName("otp")
                .variables(Map.of("otpCode", "123456"))
                .build();

        assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("user@test.local");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("SMTP down");
    }

    @Test
    void send_templateRenderFailure_persistsFailedAndThrows() {
        when(templateEngine.process(eq("email/otp"), any()))
                .thenThrow(new RuntimeException("template missing"));

        EmailRequestDto req = EmailRequestDto.builder()
                .to("user@test.local")
                .subject("Codigo")
                .templateName("otp")
                .variables(Map.of())
                .build();

        assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("otp");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailStatus.FAILED);
    }
}
