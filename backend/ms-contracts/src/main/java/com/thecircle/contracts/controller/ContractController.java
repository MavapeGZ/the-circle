package com.thecircle.contracts.controller;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractSignRequestDto;
import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.model.StoredContract;
import com.thecircle.contracts.service.ContractPdfService;
import com.thecircle.contracts.service.ContractStorageService;
import com.thecircle.contracts.service.SignatureService;
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
    private static final String SIGNATURE_MODE_CRYPTO = "CRYPTO";

    private final ContractPdfService pdfService;
    private final SignatureService signatureService;
    private final ContractStorageService storageService;

    public ContractController(ContractPdfService pdfService, SignatureService signatureService, ContractStorageService storageService) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ContractDto dto) {
        byte[] pdf = renderPdf(dto);
        StoredContract sc = persist(pdf, dto != null ? dto.getContractId() : null);
        return pdfResponse(sc);
    }

    @PostMapping(value = "/generate-and-sign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generateAndSign(@RequestBody ContractSignRequestDto req) {
        if (req == null || req.getContract() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing contract payload");
        }
        String mode = req.getSignatureMode();
        if (mode == null || !SIGNATURE_MODE_VISUAL.equalsIgnoreCase(mode)) {
            if (SIGNATURE_MODE_CRYPTO.equalsIgnoreCase(mode)) {
                throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "CRYPTO signature mode not implemented");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported signatureMode: " + mode);
        }

        byte[] pdf = renderPdf(req.getContract());
        byte[] signed;
        try {
            signed = signatureService.applyVisualSignature(pdf, req.getVisualOptions(), buildSignerMap(req.getContract()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to apply signature", ex);
        }

        StoredContract sc = persist(signed, req.getContract().getContractId());
        return pdfResponse(sc);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        StoredContract sc = storageService.get(id);
        if (sc == null) return ResponseEntity.notFound().build();
        return pdfResponse(sc);
    }

    private byte[] renderPdf(ContractDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing contract payload");
        }
        try {
            return pdfService.generatePdf(dto);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF", ex);
        }
    }

    private StoredContract persist(byte[] pdf, String originalContractId) {
        try {
            return storageService.save(pdf, originalContractId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store contract", ex);
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
}
