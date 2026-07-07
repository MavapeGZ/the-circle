package com.thecircle.contracts.controller;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractSignRequestDto;
import com.thecircle.contracts.dto.SignConfirmDto;
import com.thecircle.contracts.dto.SignConfirmResponseDto;
import com.thecircle.contracts.dto.SignRequestDto;
import com.thecircle.contracts.dto.SignRequestResponseDto;
import com.thecircle.contracts.dto.SignatureVerificationDto;
import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.model.StoredContract;
import com.thecircle.contracts.security.JwtAuthService;
import com.thecircle.contracts.service.ContractPdfService;
import com.thecircle.contracts.service.ContractService;
import com.thecircle.contracts.service.ContractStorageService;
import com.thecircle.contracts.service.SignatureService;
import com.thecircle.contracts.service.SignatureWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts/joint-rental")
public class ContractController {

    private static final String SIGNATURE_MODE_VISUAL = "VISUAL";
    private static final String SIGNATURE_MODE_ADVANCED = "ADVANCED";
    private static final String SIGNATURE_MODE_CRYPTO = "CRYPTO";

    private final ContractPdfService pdfService;
    private final SignatureService signatureService;
    private final ContractStorageService storageService;
    private final SignatureWorkflowService workflowService;
    private final ContractService contractService;
    private final JwtAuthService jwtAuthService;
    private final com.thecircle.contracts.i18n.Messages messages;

    public ContractController(ContractPdfService pdfService,
                              SignatureService signatureService,
                              ContractStorageService storageService,
                              SignatureWorkflowService workflowService,
                              ContractService contractService,
                              JwtAuthService jwtAuthService,
                              com.thecircle.contracts.i18n.Messages messages) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
        this.workflowService = workflowService;
        this.contractService = contractService;
        this.jwtAuthService = jwtAuthService;
        this.messages = messages;
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@Valid @RequestBody ContractDto dto) {
        byte[] pdf = renderPdf(dto);
        StoredContract sc = persist(pdf, dto != null ? dto.getContractId() : null);
        return pdfResponse(sc);
    }

    @PostMapping(value = "/generate-and-sign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generateAndSign(@Valid @RequestBody ContractSignRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.generate.emptyBody"));
        }
        if (req.getContract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.generate.contractFieldMissing"));
        }
        String mode = req.getSignatureMode();
        if (mode == null || !SIGNATURE_MODE_VISUAL.equalsIgnoreCase(mode)) {
            if (SIGNATURE_MODE_ADVANCED.equalsIgnoreCase(mode) || SIGNATURE_MODE_CRYPTO.equalsIgnoreCase(mode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.sign.needsOtp"));
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.sign.modeUnsupported", mode));
        }

        byte[] pdf = renderPdf(req.getContract());
        byte[] signed;
        try {
            signed = signatureService.applyVisualSignature(pdf, req.getVisualOptions(), buildSignerMap(req.getContract()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("api.error.unexpected"), ex);
        }

        StoredContract sc = persist(signed, req.getContract().getContractId());
        return pdfResponse(sc);
    }

    @PostMapping(value = "/sign/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignRequestResponseDto> signRequest(@Valid @RequestBody SignRequestDto req) {
        return ResponseEntity.ok(workflowService.requestOtp(req));
    }

    @PostMapping(value = "/sign/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignConfirmResponseDto> signConfirm(@Valid @RequestBody SignConfirmDto req,
                                                              HttpServletRequest http) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The request body is empty. Please include 'sessionId' and 'otp' and try again.");
        }
        String ip = resolveClientIp(http);
        String ua = http.getHeader("User-Agent");
        return ResponseEntity.ok(workflowService.confirm(req.getSessionId(), req.getOtp(), ip, ua));
    }

    @GetMapping("/verify/{storedContractId}")
    public ResponseEntity<SignatureVerificationDto> verify(@PathVariable String storedContractId) {
        return ResponseEntity.ok(workflowService.verify(storedContractId));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        // PII guard: the stored PDF embeds both parties' address + ID number. Verify
        // the caller and ensure they are a party to the linked contract. Fail closed
        // if the stored artifact cannot be tied back to a contract we can authorize.
        String callerId = jwtAuthService.requireUserId(authHeader);
        StoredContract sc = storageService.get(id);
        if (sc == null) return ResponseEntity.notFound().build();
        ContractDto contract = sc.getContractId() != null ? contractService.get(sc.getContractId()) : null;
        if (contract == null || !contractService.isParty(contract, callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, messages.get("api.contract.downloadForbidden"));
        }
        return pdfResponse(sc);
    }

    private byte[] renderPdf(ContractDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("api.contract.dataMissing"));
        }
        try {
            // Draft is rendered for the receiver (buyer) who initiates the deal.
            return pdfService.generatePdf(dto, contractService.getUserLanguage(dto.getReceiverId()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("api.error.unexpected"), ex);
        }
    }

    private StoredContract persist(byte[] pdf, String originalContractId) {
        try {
            return storageService.save(pdf, originalContractId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("api.error.unexpected"), ex);
        }
    }

    private ResponseEntity<byte[]> pdfResponse(StoredContract sc) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sc.getFilename() + "\"")
                .header("X-Stored-Id", sc.getId())
                .contentType(MediaType.APPLICATION_PDF)
                .body(sc.getData());
    }

    private Map<String, SignerDto> buildSignerMap(ContractDto dto) {
        Map<String, SignerDto> signers = new HashMap<>();
        if (dto.getPrimarySigner() != null) signers.put("primarySigner", dto.getPrimarySigner());
        if (dto.getSecondarySigner() != null) signers.put("secondarySigner", dto.getSecondarySigner());
        return signers;
    }

    // Stored as eIDAS audit evidence. X-Forwarded-For is client-spoofable unless a
    // trusted reverse proxy always overwrites it — this code assumes such a proxy
    // fronts the service. If the service is ever exposed directly, the persisted IP
    // cannot be trusted and this should fall back to getRemoteAddr() only.
    private String resolveClientIp(HttpServletRequest req) {
        String header = req.getHeader("X-Forwarded-For");
        if (header != null && !header.isEmpty()) {
            int comma = header.indexOf(',');
            return comma > 0 ? header.substring(0, comma).trim() : header.trim();
        }
        return req.getRemoteAddr();
    }
}
