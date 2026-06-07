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
import com.thecircle.contracts.service.ContractPdfService;
import com.thecircle.contracts.service.ContractStorageService;
import com.thecircle.contracts.service.SignatureService;
import com.thecircle.contracts.service.SignatureWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
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

    public ContractController(ContractPdfService pdfService,
                              SignatureService signatureService,
                              ContractStorageService storageService,
                              SignatureWorkflowService workflowService) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
        this.workflowService = workflowService;
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ContractDto dto) {
        byte[] pdf = renderPdf(dto);
        StoredContract sc = persist(pdf, dto != null ? dto.getContractId() : null);
        return pdfResponse(sc);
    }

    @PostMapping(value = "/generate-and-sign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generateAndSign(@RequestBody ContractSignRequestDto req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The request body is empty. Please include the contract data and try again.");
        }
        if (req.getContract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The 'contract' field is missing from the request. Please include the full contract data and try again.");
        }
        String mode = req.getSignatureMode();
        if (mode == null || !SIGNATURE_MODE_VISUAL.equalsIgnoreCase(mode)) {
            if (SIGNATURE_MODE_ADVANCED.equalsIgnoreCase(mode) || SIGNATURE_MODE_CRYPTO.equalsIgnoreCase(mode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Advanced and cryptographic signatures need an OTP. Please start the signing flow with /sign/request and then /sign/confirm.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The signature mode '" + mode + "' is not supported. Please use 'VISUAL', 'ADVANCED' or 'CRYPTO'.");
        }

        byte[] pdf = renderPdf(req.getContract());
        byte[] signed;
        try {
            signed = signatureService.applyVisualSignature(pdf, req.getVisualOptions(), buildSignerMap(req.getContract()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error. Please contact our support team.", ex);
        }

        StoredContract sc = persist(signed, req.getContract().getContractId());
        return pdfResponse(sc);
    }

    @PostMapping(value = "/sign/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignRequestResponseDto> signRequest(@RequestBody SignRequestDto req) {
        return ResponseEntity.ok(workflowService.requestOtp(req));
    }

    @PostMapping(value = "/sign/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignConfirmResponseDto> signConfirm(@RequestBody SignConfirmDto req,
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
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        StoredContract sc = storageService.get(id);
        if (sc == null) return ResponseEntity.notFound().build();
        return pdfResponse(sc);
    }

    private byte[] renderPdf(ContractDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The contract data is missing from the request. Please fill in the contract form and try again.");
        }
        try {
            return pdfService.generatePdf(dto);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error. Please contact our support team.", ex);
        }
    }

    private StoredContract persist(byte[] pdf, String originalContractId) {
        try {
            return storageService.save(pdf, originalContractId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error. Please contact our support team.", ex);
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
