package com.thecircle.notifications.service;

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

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailDispatcherTest {

    private JavaMailSender mailSender;
    private EmailLogRepository repository;
    private EmailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        repository = mock(EmailLogRepository.class);
        dispatcher = new EmailDispatcher(mailSender, repository);
        ReflectionTestUtils.setField(dispatcher, "fromAddress", "no-reply@test.local");

        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
        when(repository.findById(42L)).thenReturn(Optional.of(
                EmailLog.builder().id(42L).toAddress("user@test.local").status(EmailStatus.QUEUED).build()));
    }

    @Test
    void dispatch_success_marksLogSent() {
        dispatcher.dispatch(42L, "user@test.local", "Codigo", "<p>123456</p>");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void dispatch_smtpFailure_marksLogFailedAndDoesNotThrow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> dispatcher.dispatch(42L, "user@test.local", "Codigo", "<p>x</p>"))
                .doesNotThrowAnyException();

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(saved.getErrorMessage()).contains("SMTP down");
    }
}
