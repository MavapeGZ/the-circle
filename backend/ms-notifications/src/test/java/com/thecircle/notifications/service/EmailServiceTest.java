package com.thecircle.notifications.service;

import com.thecircle.notifications.dto.EmailRequestDto;
import com.thecircle.notifications.dto.EmailResponseDto;
import com.thecircle.notifications.model.EmailLog;
import com.thecircle.notifications.model.EmailStatus;
import com.thecircle.notifications.repository.EmailLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private SpringTemplateEngine templateEngine;
    private EmailLogRepository repository;
    private EmailDispatcher dispatcher;
    private EmailService service;

    @BeforeEach
    void setUp() {
        templateEngine = mock(SpringTemplateEngine.class);
        repository = mock(EmailLogRepository.class);
        dispatcher = mock(EmailDispatcher.class);
        service = new EmailService(templateEngine, repository, dispatcher);

        when(templateEngine.process(eq("email/otp"), any())).thenReturn("<p>123456</p>");
        when(repository.save(any(EmailLog.class))).thenAnswer(inv -> {
            EmailLog log = inv.getArgument(0);
            log.setId(42L);
            return log;
        });
    }

    @Test
    void send_queuesEmailAndDelegatesToDispatcher() {
        EmailRequestDto req = EmailRequestDto.builder()
                .to("user@test.local")
                .subject("Codigo")
                .templateName("otp")
                .variables(Map.of("otpCode", "123456"))
                .build();

        EmailResponseDto response = service.send(req);

        assertThat(response.getStatus()).isEqualTo(EmailStatus.QUEUED);
        assertThat(response.getSentAt()).isNull();
        assertThat(response.getId()).isEqualTo(42L);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(repository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmailStatus.QUEUED);
        assertThat(saved.getToAddress()).isEqualTo("user@test.local");
        assertThat(saved.getSentAt()).isNull();
        assertThat(saved.getErrorMessage()).isNull();

        verify(dispatcher).dispatch(42L, "user@test.local", "Codigo", "<p>123456</p>");
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
                .hasMessageContaining("support");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmailStatus.FAILED);

        verify(dispatcher, never()).dispatch(any(), any(), any(), any());
    }
}
