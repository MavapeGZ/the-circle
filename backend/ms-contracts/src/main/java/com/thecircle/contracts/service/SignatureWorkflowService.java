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
    private final ContractService contractService;
    private final OtpDeliveryChannel otpDelivery;
    private final com.thecircle.contracts.client.GamificationClient gamificationClient;
    private final com.thecircle.contracts.i18n.Messages messages;

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
                                    ContractService contractService,
                                    OtpDeliveryChannel otpDelivery,
                                    com.thecircle.contracts.client.GamificationClient gamificationClient,
                                    com.thecircle.contracts.i18n.Messages messages) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
        this.contractService = contractService;
        this.otpDelivery = otpDelivery;
        this.gamificationClient = gamificationClient;
        this.messages = messages;
    }

    public SignRequestResponseDto requestOtp(SignRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.sign.emptyBody"));
        }
        if (req.getSignerEmail() == null || req.getSignerEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.sign.emailMissing"));
        }
        if (req.getContract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.sign.contractMissing"));
        }
        String mode = req.getSignatureMode();
        if (mode != null && !SIGNATURE_MODE_ADVANCED.equalsIgnoreCase(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.sign.modeUnsupportedEmail", mode));
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
                otpDelivery.send(req.getSignerEmail(), rawOtp,
                        resolveSignerName(req.getContract(), req.getSignerEmail()),
                        resolveSignerLocale(req.getContract(), req.getSignerRole()));
            } catch (RuntimeException ex) {
                sessions.remove(sessionId);
                log.error("Failed to send OTP email to {}", req.getSignerEmail(), ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, messages.get("api.sign.emailSendFailed"), ex);
            }
        }

        SignRequestResponseDto resp = new SignRequestResponseDto();
        resp.setSessionId(sessionId);
        if (exposeOtp) {
            resp.setMessage(messages.get("api.sign.otpDev", req.getSignerEmail()));
            resp.setOtp(rawOtp);
        } else {
            resp.setMessage(messages.get("api.sign.otpSent", req.getSignerEmail()));
        }
        return resp;
    }

    public SignConfirmResponseDto confirm(String sessionId, String otp, String ip, String ua) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.confirm.sessionMissing"));
        }
        if (otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.confirm.otpMissing"));
        }

        OtpSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("api.confirm.sessionNotFound"));
        }
        if (session.used) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, messages.get("api.confirm.sessionUsed"));
        }
        if (Instant.now().isAfter(session.expiry)) {
            sessions.remove(sessionId);
            throw new ResponseStatusException(HttpStatus.GONE, messages.get("api.confirm.expired"));
        }

        session.attempts++;
        if (session.attempts > maxAttempts) {
            sessions.remove(sessionId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, messages.get("api.confirm.tooMany"));
        }

        if (!OTP_ENCODER.matches(otp, session.hashedOtp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, messages.get("api.confirm.incorrect"));
        }

        // Claim the session before any side effect so two concurrent confirms
        // with the same valid OTP cannot both produce a signed contract.
        if (!session.claim()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, messages.get("api.confirm.sessionUsed"));
        }

        // Resolve the signing role up front so we can stamp this party's signature
        // onto the rendered document before generating it. Capture a single signing
        // instant so the timestamp printed on the PDF matches the one persisted below.
        SignerRole role = session.signerRole != null ? session.signerRole : SignerRole.RECEIVER;
        LocalDateTime signedAt = LocalDateTime.now();

        StoredContract sc;
        try {
            // Fill signer names from ms-users so the signed PDF is not anonymous.
            // Reuse the profiles fetched here for the PDF locale instead of
            // re-querying ms-users for the same signer.
            ContractService.SignerProfiles profiles =
                    contractService.enrichSigners(session.contract, session.signerEmail);
            // Stamp this signer's timestamp (and carry over the counterparty's, if
            // they already signed) so the signature zone renders the custom digital
            // signature now, and a fully-signed contract shows both side by side.
            stampSignatureTimestamps(session.contract, role, signedAt);
            byte[] pdf = pdfService.generatePdf(session.contract,
                    profiles.languageFor(session.signerRole));
            if (session.visualOptions != null) {
                pdf = signatureService.applyVisualSignature(pdf, session.visualOptions, buildSignerMap(session.contract));
            }
            sc = storageService.saveWithAudit(pdf, session.contract.getContractId(), session.signerEmail, ip, ua);
        } catch (IOException | RuntimeException ex) {
            // Release the claim so the signer can retry with the same OTP while it is still valid.
            session.release();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("api.error.unexpected"), ex);
        }

        // Record this party's signature and link the stored PDF. The contract turns
        // ACTIVE only once both receiver and owner have signed.
        ContractDto updated = contractService.markSigned(session.contract.getContractId(), sc.getId(), role, signedAt);

        boolean fullySigned = updated != null && updated.getStatus() == ContractStatus.ACTIVE;

        SignConfirmResponseDto resp = new SignConfirmResponseDto();
        resp.setSuccess(true);
        resp.setFullySigned(fullySigned);
        resp.setStoredContractId(sc.getId());
        resp.setDownloadUrl(DOWNLOAD_PATH + sc.getId());
        resp.setSignedAt(signedAt);
        resp.setMessage(messages.get("api.confirm.success"));
        // The deal just closed: award the gamification event (donation/rental/sale)
        // and, when this signer is the rewarded party, return the unlocked badges so
        // the UI can toast them. Best-effort — never lets gamification break signing.
        if (fullySigned) {
            resp.setEarnedBadges(gamificationClient.awardForActivation(updated, role));
        }
        return resp;
    }

    public SignatureVerificationDto verify(String storedContractId) {
        StoredContract sc = storageService.get(storedContractId);
        if (sc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, messages.get("api.verify.notFound"));
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

    /**
     * Best-effort lookup of the signing party's preferred language so the OTP
     * email is localized. RECEIVER maps to receiverId, OWNER to ownerId (mirrors
     * enrichSigners). Returns null on any gap so ms-notifications falls back to
     * English; a users-service hiccup never blocks signing.
     */
    private String resolveSignerLocale(ContractDto contract, SignerRole role) {
        if (contract == null || role == null) return null;
        String userId = role == SignerRole.RECEIVER ? contract.getReceiverId() : contract.getOwnerId();
        return contractService.getUserLanguage(userId);
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Sets the signing party's signature timestamp on the contract used to render
     * the PDF, carrying over the counterparty's already-persisted timestamp so a
     * fully-signed document shows both signatures. Best-effort: a failed lookup
     * just means the counterparty's prior signature may not appear on this render.
     */
    private void stampSignatureTimestamps(ContractDto contract, SignerRole role, LocalDateTime now) {
        if (contract == null) return;
        ContractDto persisted = contract.getContractId() != null
                ? contractService.get(contract.getContractId()) : null;
        if (persisted != null) {
            if (contract.getReceiverSignedAt() == null) {
                contract.setReceiverSignedAt(persisted.getReceiverSignedAt());
            }
            if (contract.getOwnerSignedAt() == null) {
                contract.setOwnerSignedAt(persisted.getOwnerSignedAt());
            }
        }
        if (role == SignerRole.OWNER) {
            contract.setOwnerSignedAt(now);
        } else {
            contract.setReceiverSignedAt(now);
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
