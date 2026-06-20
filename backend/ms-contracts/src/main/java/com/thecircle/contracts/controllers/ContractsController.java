package com.thecircle.contracts.controllers;

import com.thecircle.contracts.dto.ContractCreateRequest;
import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.security.JwtAuthService;
import com.thecircle.contracts.service.ContractPdfService;
import com.thecircle.contracts.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractsController {

    private final ContractService contractService;
    private final ContractPdfService pdfService;
    private final JwtAuthService jwtAuthService;

    public ContractsController(ContractService contractService, ContractPdfService pdfService,
                               JwtAuthService jwtAuthService) {
        this.contractService = contractService;
        this.pdfService = pdfService;
        this.jwtAuthService = jwtAuthService;
    }

    @GetMapping("/health")
    public String health() {
        return "ms-contracts OK";
    }

    @PostMapping
    public ResponseEntity<ContractDto> createContract(@Valid @RequestBody ContractCreateRequest request) {
        if (request == null || request.itemId() == null || request.receiverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId and receiverId are required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.create(request));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDto> getContract(@PathVariable String contractId) {
        ContractDto dto = contractService.get(contractId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ContractDto>> getUserContracts(@PathVariable String userId) {
        return ResponseEntity.ok(contractService.getByUser(userId));
    }

    @GetMapping("/{contractId}/pdf")
    public ResponseEntity<byte[]> downloadContractPdf(
            @PathVariable String contractId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        // PII guard: the rendered PDF embeds both parties' address + ID number, so
        // only the contract's owner or receiver may download it.
        String callerId = jwtAuthService.requireUserId(authHeader);
        ContractDto dto = contractService.get(contractId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        if (!contractService.isParty(dto, callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a party to this contract");
        }
        contractService.enrichSigners(dto, null);
        byte[] pdf;
        try {
            pdf = pdfService.generatePdf(dto);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF", ex);
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"contract-" + contractId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{contractId}/guarantee/deposit")
    public ResponseEntity<ContractDto> depositGuarantee(@PathVariable String contractId) {
        return ResponseEntity.ok(contractService.depositGuarantee(contractId));
    }

    @PostMapping("/{contractId}/guarantee/release")
    public ResponseEntity<ContractDto> releaseGuarantee(@PathVariable String contractId) {
        return ResponseEntity.ok(contractService.releaseGuarantee(contractId));
    }

    @PostMapping("/{contractId}/guarantee/claim")
    public ResponseEntity<ContractDto> claimGuarantee(@PathVariable String contractId) {
        return ResponseEntity.ok(contractService.claimGuarantee(contractId));
    }
}
