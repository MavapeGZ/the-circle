package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.*;
import com.thecircle.contracts.model.StoredContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureWorkflowServiceTest {

    @Mock
    private ContractPdfService pdfService;

    @Mock
    private SignatureService signatureService;

    @Mock
    private ContractStorageService storageService;

    @Mock
    private ContractService contractService;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SignatureWorkflowService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "otpLength", 6);
        ReflectionTestUtils.setField(service, "otpTtlSeconds", 600);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "exposeOtp", false);
    }

    // --- helpers ---

    private ContractDto buildContract() {
        ContractDto dto = new ContractDto();
        dto.setContractId("contract-123");
        SignerDto s1 = new SignerDto();
        s1.setFullName("Juan Perez");
        s1.setEmail("juan@example.com");
        dto.setPrimarySigner(s1);
        return dto;
    }

    private SignRequestDto buildRequest() {
        SignRequestDto req = new SignRequestDto();
        req.setSignerEmail("signer@example.com");
        req.setContract(buildContract());
        return req;
    }

    private StoredContract buildStoredContract() {
        return new StoredContract("sc-1", "sc-1.pdf", "contract-123", "PDF".getBytes(), LocalDateTime.now());
    }

    // --- requestOtp ---

    @Test
    void requestOtp_shouldReturnSessionIdAndSendEmail() {
        SignRequestResponseDto resp = service.requestOtp(buildRequest());
        assertNotNull(resp.getSessionId());
        assertNotNull(resp.getMessage());
        assertNull(resp.getOtp());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestOtp_withExposeOtp_shouldIncludeRawOtpAndSkipEmail() {
        ReflectionTestUtils.setField(service, "exposeOtp", true);
        SignRequestResponseDto resp = service.requestOtp(buildRequest());
        assertNotNull(resp.getOtp());
        assertEquals(6, resp.getOtp().length());
        assertTrue(resp.getOtp().matches("\\d{6}"));
    }

    @Test
    void requestOtp_mailFailure_throws502() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestOtp(buildRequest()));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void requestOtp_unsupportedMode_throws400() {
        SignRequestDto req = buildRequest();
        req.setSignatureMode("CRYPTO");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestOtp(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void requestOtp_nullRequest_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestOtp(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void requestOtp_missingEmail_throws400() {
        SignRequestDto req = buildRequest();
        req.setSignerEmail(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestOtp(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void requestOtp_missingContract_throws400() {
        SignRequestDto req = buildRequest();
        req.setContract(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestOtp(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --- confirm ---

    @Test
    void confirm_happyPath_returnsStoredContractIdWithAudit() throws IOException {
        ReflectionTestUtils.setField(service, "exposeOtp", true);
        SignRequestResponseDto init = service.requestOtp(buildRequest());

        when(pdfService.generatePdf(any())).thenReturn("PDF".getBytes());
        when(storageService.saveWithAudit(any(), any(), any(), any(), any())).thenReturn(buildStoredContract());

        SignConfirmResponseDto resp = service.confirm(init.getSessionId(), init.getOtp(), "127.0.0.1", "TestAgent");

        assertTrue(resp.isSuccess());
        assertEquals("sc-1", resp.getStoredContractId());
        assertNotNull(resp.getDownloadUrl());
        assertNotNull(resp.getSignedAt());
        verify(storageService).saveWithAudit(any(), any(), eq("signer@example.com"), eq("127.0.0.1"), eq("TestAgent"));
    }

    @Test
    void confirm_invalidOtp_throws401() {
        SignRequestResponseDto init = service.requestOtp(buildRequest());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(init.getSessionId(), "000000", "127.0.0.1", "UA"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void confirm_unknownSession_throws404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm("no-such-session", "123456", "127.0.0.1", "UA"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void confirm_expiredOtp_throws410() {
        ReflectionTestUtils.setField(service, "exposeOtp", true);
        ReflectionTestUtils.setField(service, "otpTtlSeconds", -1);
        SignRequestResponseDto init = service.requestOtp(buildRequest());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(init.getSessionId(), init.getOtp(), "127.0.0.1", "UA"));
        assertEquals(HttpStatus.GONE, ex.getStatusCode());
    }

    @Test
    void confirm_reuseAfterSuccess_throws409() throws IOException {
        ReflectionTestUtils.setField(service, "exposeOtp", true);
        SignRequestResponseDto init = service.requestOtp(buildRequest());

        when(pdfService.generatePdf(any())).thenReturn("PDF".getBytes());
        when(storageService.saveWithAudit(any(), any(), any(), any(), any())).thenReturn(buildStoredContract());

        service.confirm(init.getSessionId(), init.getOtp(), "127.0.0.1", "UA");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(init.getSessionId(), init.getOtp(), "127.0.0.1", "UA"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void confirm_pdfFailure_releasesSessionForRetry() throws IOException {
        ReflectionTestUtils.setField(service, "exposeOtp", true);
        SignRequestResponseDto init = service.requestOtp(buildRequest());

        when(pdfService.generatePdf(any()))
                .thenThrow(new IOException("boom"))
                .thenReturn("PDF".getBytes());
        when(storageService.saveWithAudit(any(), any(), any(), any(), any())).thenReturn(buildStoredContract());

        ResponseStatusException first = assertThrows(ResponseStatusException.class,
                () -> service.confirm(init.getSessionId(), init.getOtp(), "127.0.0.1", "UA"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, first.getStatusCode());

        SignConfirmResponseDto retry = service.confirm(init.getSessionId(), init.getOtp(), "127.0.0.1", "UA");
        assertTrue(retry.isSuccess());
    }

    @Test
    void confirm_maxAttemptsExceeded_throws429() {
        ReflectionTestUtils.setField(service, "maxAttempts", 2);
        SignRequestResponseDto init = service.requestOtp(buildRequest());
        String sid = init.getSessionId();

        assertThrows(ResponseStatusException.class, () -> service.confirm(sid, "000000", "ip", "ua"));
        assertThrows(ResponseStatusException.class, () -> service.confirm(sid, "000000", "ip", "ua"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(sid, "000000", "ip", "ua"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void confirm_nullSessionId_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(null, "123456", "127.0.0.1", "UA"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void confirm_nullOtp_throws400() {
        SignRequestResponseDto init = service.requestOtp(buildRequest());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirm(init.getSessionId(), null, "127.0.0.1", "UA"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --- verify ---

    @Test
    void verify_existingContract_returnsVerified() {
        when(storageService.get("sc-1")).thenReturn(buildStoredContract());

        SignatureVerificationDto dto = service.verify("sc-1");

        assertTrue(dto.isVerified());
        assertEquals("sc-1", dto.getStoredContractId());
        assertEquals("contract-123", dto.getContractId());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void verify_notFound_throws404() {
        when(storageService.get("nonexistent")).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.verify("nonexistent"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
