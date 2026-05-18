package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.*;
import com.thecircle.contracts.model.StoredContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SignatureWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(SignatureWorkflowService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, OtpSession> sessions = new ConcurrentHashMap<>();

    private final ContractPdfService pdfService;
    private final SignatureService signatureService;
    private final ContractStorageService storageService;

    @Value("${signature.otp.length:6}")
    private int otpLength;

    @Value("${signature.otp.ttl-seconds:600}")
    private int otpTtlSeconds;

    @Value("${signature.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${signature.otp.expose-in-response:false}")
    private boolean exposeOtp;

    @Value("${signature.hash-algorithm:SHA-256}")
    private String hashAlgorithm;

    public SignatureWorkflowService(ContractPdfService pdfService,
                                    SignatureService signatureService,
                                    ContractStorageService storageService) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
    }

    public SignRequestResponseDto requestOtp(SignRequestDto req) {
        if (req == null || req.getSignerEmail() == null || req.getContract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signerEmail and contract are required");
        }

        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);
        String sessionId = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(otpTtlSeconds);

        sessions.put(sessionId, new OtpSession(hashedOtp, expiry, req.getSignerEmail(), req.getContract(), req.getVisualOptions()));

        log.info("OTP session created for {}. Session: {}", req.getSignerEmail(), sessionId);
        if (exposeOtp) {
            log.warn("OTP_EXPOSE_DEV enabled — OTP for {}: {}", req.getSignerEmail(), rawOtp);
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

        if (!hashOtp(otp).equals(session.hashedOtp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }

        session.used = true;
        sessions.remove(sessionId);

        byte[] pdf;
        try {
            pdf = pdfService.generatePdf(session.contract);
            if (session.visualOptions != null) {
                pdf = signatureService.applyVisualSignature(pdf, session.visualOptions, buildSignerMap(session.contract));
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate signed PDF", ex);
        }

        LocalDateTime signedAt = LocalDateTime.now();
        StoredContract sc = storageService.save(pdf, session.contract.getContractId());

        SignConfirmResponseDto resp = new SignConfirmResponseDto();
        resp.setSuccess(true);
        resp.setStoredContractId(sc.getId());
        resp.setDownloadUrl("/api/contracts/joint-rental/download/" + sc.getId());
        resp.setSignedAt(signedAt);
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

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest md = MessageDigest.getInstance(hashAlgorithm);
            byte[] digest = md.digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Hash algorithm not available: " + hashAlgorithm, ex);
        }
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
    }
}
