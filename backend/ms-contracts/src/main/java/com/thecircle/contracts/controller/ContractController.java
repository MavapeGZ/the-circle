package com.thecircle.contracts.controller;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractSignRequestDto;
import com.thecircle.contracts.model.StoredContract;
import com.thecircle.contracts.service.ContractPdfService;
import com.thecircle.contracts.service.ContractStorageService;
import com.thecircle.contracts.service.SignatureService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/joint-rental")
public class ContractController {

    private final ContractPdfService pdfService;
    private final SignatureService signatureService;
    private final ContractStorageService storageService;

    public ContractController(ContractPdfService pdfService, SignatureService signatureService, ContractStorageService storageService) {
        this.pdfService = pdfService;
        this.signatureService = signatureService;
        this.storageService = storageService;
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody ContractDto dto) throws Exception {
        byte[] pdf = pdfService.generatePdf(dto);
        StoredContract sc = storageService.save(pdf, dto.getContractId());
        String filename = sc.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Stored-Id", sc.getId())
                .contentType(MediaType.APPLICATION_PDF)
                .body(sc.getData());
    }

    @PostMapping(value = "/generate-and-sign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generateAndSign(@RequestBody ContractSignRequestDto req) throws Exception {
        byte[] pdf = pdfService.generatePdf(req.getContract());
        if ("VISUAL".equalsIgnoreCase(req.getSignatureMode())) {
            pdf = signatureService.applyVisualSignature(pdf, req.getVisualOptions());
        } else {
            pdf = signatureService.applyVisualSignature(pdf, req.getVisualOptions());
        }
        StoredContract sc = storageService.save(pdf, req.getContract().getContractId());
        String filename = sc.getFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Stored-Id", sc.getId())
                .contentType(MediaType.APPLICATION_PDF)
                .body(sc.getData());
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        StoredContract sc = storageService.get(id);
        if (sc == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sc.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(sc.getData());
    }
}
