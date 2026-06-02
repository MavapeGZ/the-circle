package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.*;
import com.thecircle.contracts.model.StoredContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final OtpDeliveryChannel otpDelivery;

    @Value("${signature.otp.length:6}")
    private int otpLength;

    @Value("${signature.otp.ttl-seconds:600}")
    private int otpTtlSeconds;

    @Value("${signature.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${signature.otp.expose-in-response:false}")
    private boolean exposeOtp;

    public SignatureWorkflowService(ContractPdfService pdfService,
                                    SignatureService signatureService,
                                    ContractStorageService storageService,
                                    OtpDeliveryChannel otpDelivery) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
        this.otpDelivery = otpDelivery;
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

        sessions.put(sessionId, new OtpSession(hashedOtp, expiry, req.getSignerEmail(), req.getContract(), req.getVisualOptions()));
        log.info("OTP session created for {}. Session: {}", req.getSignerEmail(), sessionId);

        if (exposeOtp) {
            log.warn("OTP_EXPOSE_DEV enabled — OTP for {}: {}", req.getSignerEmail(), rawOtp);
        } else {
            try {
                otpDelivery.send(req.getSignerEmail(), rawOtp, resolveSignerName(req.getContract(), req.getSignerEmail()));
            } catch (RuntimeException ex) {
                sessions.remove(sessionId);
                log.error("Failed to send OTP email to {}", req.getSignerEmail(), ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to send OTP email", ex);
            }
        }

        SignRequestResponseDto resp = new SignRequestResponseDto();
        resp.setSessionId(sessionId);
        if (exposeOtp) {
            resp.setMessage("OTP generated (dev mode, not sent) for " + req.getSignerEmail());
            resp.setOtp(rawOtp);
        } else {
            resp.setMessage("OTP sent to " + req.getSignerEmail());
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

        SignConfirmResponseDto resp = new SignConfirmResponseDto();
        resp.setSuccess(true);
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

    private String resolveSignerName(ContractDto contract, String signerEmail) {
        if (contract == null || signerEmail == null) return null;
        SignerDto primary = contract.getPrimarySigner();
        if (primary != null && signerEmail.equalsIgnoreCase(primary.getEmail())) {
            return primary.getFullName();
        }
        SignerDto secondary = contract.getSecondarySigner();
        if (secondary != null && signerEmail.equalsIgnoreCase(secondary.getEmail())) {
            return secondary.getFullName();
        }
        return null;
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
        final ContractDto contract;
        final VisualSignatureDto visualOptions;
        int attempts;
        boolean used;

        OtpSession(String hashedOtp, Instant expiry, String signerEmail,
                   ContractDto contract, VisualSignatureDto visualOptions) {
            this.hashedOtp = hashedOtp;
            this.expiry = expiry;
            this.signerEmail = signerEmail;
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
