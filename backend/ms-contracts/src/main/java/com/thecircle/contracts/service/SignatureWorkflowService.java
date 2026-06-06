package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.*;
import com.thecircle.contracts.model.StoredContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP-based advanced electronic signature workflow.
 *
 * NOTE: OTP sessions are held in process memory. This service assumes a single
 * instance — a request served by one node cannot be confirmed on another, and
 * pending sessions are lost on restart. Move sessions to a shared store (Redis)
 * before scaling ms-contracts horizontally.
 */
@Service
public class SignatureWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(SignatureWorkflowService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder OTP_ENCODER = new BCryptPasswordEncoder();
    private static final String SIGNATURE_MODE_ADVANCED = "ADVANCED";
    private static final String DOWNLOAD_PATH = "/api/contracts/joint-rental/download/";

    private final ConcurrentHashMap<String, OtpSession> sessions = new ConcurrentHashMap<>();

    private final ContractPdfService pdfService;
    private final SignatureService signatureService;
    private final ContractStorageService storageService;
    private final ContractService contractService;
    private final JavaMailSender mailSender;

    @Value("${signature.otp.length:6}")
    private int otpLength;

    @Value("${signature.otp.ttl-seconds:600}")
    private int otpTtlSeconds;

    @Value("${signature.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${signature.otp.expose-in-response:false}")
    private boolean exposeOtp;

    @Value("${signature.mail.from:no-reply@thecircle.local}")
    private String mailFrom;

    @Value("${signature.mail.subject:The Circle - Codigo de firma electronica}")
    private String mailSubject;

    public SignatureWorkflowService(ContractPdfService pdfService,
                                    SignatureService signatureService,
                                    ContractStorageService storageService,
                                    ContractService contractService,
                                    JavaMailSender mailSender) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
        this.contractService = contractService;
        this.mailSender = mailSender;
    }

    public SignRequestResponseDto requestOtp(SignRequestDto req) {
        if (req == null || req.getSignerEmail() == null || req.getContract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signerEmail and contract are required");
        }
        String mode = req.getSignatureMode();
        if (mode != null && !SIGNATURE_MODE_ADVANCED.equalsIgnoreCase(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported signatureMode for OTP flow: " + mode);
        }

        String rawOtp = generateOtp();
        String hashedOtp = OTP_ENCODER.encode(rawOtp);
        String sessionId = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(otpTtlSeconds);

        sessions.put(sessionId, new OtpSession(hashedOtp, expiry, req.getSignerEmail(), req.getSignerRole(), req.getContract(), req.getVisualOptions()));
        log.info("OTP session created for {}. Session: {}", req.getSignerEmail(), sessionId);

        if (exposeOtp) {
            log.warn("OTP_EXPOSE_DEV enabled — OTP for {}: {}", req.getSignerEmail(), rawOtp);
        } else {
            try {
                sendOtpEmail(req.getSignerEmail(), rawOtp);
            } catch (MailException ex) {
                sessions.remove(sessionId);
                log.error("Failed to send OTP email to {}", req.getSignerEmail(), ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to send OTP email", ex);
            }
        }

        SignRequestResponseDto resp = new SignRequestResponseDto();
        resp.setSessionId(sessionId);
        resp.setMessage("OTP sent to " + req.getSignerEmail());
        if (exposeOtp) {
            resp.setOtp(rawOtp);
        }
        return resp;
    }

    public SignConfirmResponseDto confirm(String sessionId, String otp, String ip, String ua) {
        if (sessionId == null || otp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId and otp are required");
        }

        OtpSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or expired");
        }
        if (session.used) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already used");
        }
        if (Instant.now().isAfter(session.expiry)) {
            sessions.remove(sessionId);
            throw new ResponseStatusException(HttpStatus.GONE, "OTP expired");
        }

        session.attempts++;
        if (session.attempts > maxAttempts) {
            sessions.remove(sessionId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Max OTP attempts exceeded");
        }

        if (!OTP_ENCODER.matches(otp, session.hashedOtp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }

        // Claim the session before any side effect so two concurrent confirms
        // with the same valid OTP cannot both produce a signed contract.
        if (!session.claim()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already used");
        }

        StoredContract sc;
        try {
            // Fill signer names from ms-users so the signed PDF is not anonymous.
            contractService.enrichSigners(session.contract, session.signerEmail);
            byte[] pdf = pdfService.generatePdf(session.contract);
            if (session.visualOptions != null) {
                pdf = signatureService.applyVisualSignature(pdf, session.visualOptions, buildSignerMap(session.contract));
            }
            sc = storageService.saveWithAudit(pdf, session.contract.getContractId(), session.signerEmail, ip, ua);
        } catch (IOException | RuntimeException ex) {
            // Release the claim so the signer can retry with the same OTP while it is still valid.
            session.release();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate signed PDF", ex);
        }

        // Record this party's signature and link the stored PDF. The contract turns
        // ACTIVE only once both receiver and owner have signed.
        SignerRole role = session.signerRole != null ? session.signerRole : SignerRole.RECEIVER;
        ContractDto updated = contractService.markSigned(session.contract.getContractId(), sc.getId(), role);

        SignConfirmResponseDto resp = new SignConfirmResponseDto();
        resp.setSuccess(true);
        resp.setFullySigned(updated != null && updated.getStatus() == ContractStatus.ACTIVE);
        resp.setStoredContractId(sc.getId());
        resp.setDownloadUrl(DOWNLOAD_PATH + sc.getId());
        resp.setSignedAt(LocalDateTime.now());
        resp.setMessage("Contract signed successfully");
        return resp;
    }

    public SignatureVerificationDto verify(String storedContractId) {
        StoredContract sc = storageService.get(storedContractId);
        if (sc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored contract not found");
        }

        SignatureVerificationDto dto = new SignatureVerificationDto();
        dto.setStoredContractId(sc.getId());
        dto.setContractId(sc.getContractId());
        dto.setFilename(sc.getFilename());
        dto.setVerified(true);
        dto.setCreatedAt(sc.getCreatedAt());
        return dto;
    }

    /** Evicts expired OTP sessions so abandoned requests do not leak memory. */
    @Scheduled(fixedDelayString = "${signature.otp.cleanup-interval-ms:120000}")
    public void purgeExpiredSessions() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.values().removeIf(s -> now.isAfter(s.expiry));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.debug("Purged {} expired OTP session(s)", removed);
        }
    }

    private void sendOtpEmail(String to, String otp) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(to);
        msg.setSubject(mailSubject);
        msg.setText("Your one-time signature code is: " + otp
                + "\nIt is valid for " + (otpTtlSeconds / 60) + " minutes."
                + "\nIf you did not request this, ignore this email.");
        mailSender.send(msg);
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private Map<String, SignerDto> buildSignerMap(ContractDto contract) {
        Map<String, SignerDto> signers = new HashMap<>();
        if (contract.getPrimarySigner() != null) signers.put("primarySigner", contract.getPrimarySigner());
        if (contract.getSecondarySigner() != null) signers.put("secondarySigner", contract.getSecondarySigner());
        return signers;
    }

    private static class OtpSession {
        final String hashedOtp;
        final Instant expiry;
        final String signerEmail;
        final SignerRole signerRole;
        final ContractDto contract;
        final VisualSignatureDto visualOptions;
        int attempts;
        boolean used;

        OtpSession(String hashedOtp, Instant expiry, String signerEmail, SignerRole signerRole,
                   ContractDto contract, VisualSignatureDto visualOptions) {
            this.hashedOtp = hashedOtp;
            this.expiry = expiry;
            this.signerEmail = signerEmail;
            this.signerRole = signerRole;
            this.contract = contract;
            this.visualOptions = visualOptions;
        }

        synchronized boolean claim() {
            if (used) return false;
            used = true;
            return true;
        }

        synchronized void release() {
            used = false;
        }
    }
}
