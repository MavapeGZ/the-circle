package com.thecircle.contracts.controllers;

import com.thecircle.contracts.dto.ContractCreateRequest;
import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
public class ContractsController {

    @GetMapping("/health")
    public String health() {
        return "ms-contracts OK";
    }

    @PostMapping
    public ResponseEntity<ContractDto> createContract(@RequestBody ContractCreateRequest request) {
        return ResponseEntity.ok(new ContractDto(
            "c1",
            request.itemId(),
            request.ownerId(),
            request.receiverId(),
            request.type(),
            ContractStatus.DRAFT,
            request.guaranteeAmount(),
            request.conditions(),
            request.returnDate(),
            LocalDateTime.now(),
            null
        ));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ContractDto> getContract(@PathVariable String contractId) {
        return ResponseEntity.ok(new ContractDto(
            contractId, "i1", "u1", "u2", com.thecircle.contracts.dto.ContractType.SALE, ContractStatus.ACTIVE,
            new BigDecimal("0"), "Condiciones de prueba", null, LocalDateTime.now(), LocalDateTime.now()
        ));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ContractDto>> getUserContracts(@PathVariable String userId) {
        return ResponseEntity.ok(List.of(
            new ContractDto("c1", "i1", userId, "u2", com.thecircle.contracts.dto.ContractType.RENT, ContractStatus.ACTIVE,
            new BigDecimal("50"), "Condiciones", LocalDateTime.now().plusDays(5), LocalDateTime.now(), LocalDateTime.now())
        ));
    }

    @PostMapping("/{contractId}/sign")
    public ResponseEntity<ContractDto> signContract(@PathVariable String contractId) {
        return ResponseEntity.ok(new ContractDto(
            contractId, "i1", "u1", "u2", com.thecircle.contracts.dto.ContractType.SALE, ContractStatus.ACTIVE,
            new BigDecimal("0"), "Condiciones", null, LocalDateTime.now(), LocalDateTime.now()
        ));
    }

    @GetMapping("/{contractId}/pdf")
    public ResponseEntity<byte[]> downloadContractPdf(@PathVariable String contractId) {
        byte[] mockPdf = "MOCK PDF CONTENT".getBytes();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"contract-" + contractId + ".pdf\"")
                .body(mockPdf);
    }

    @PostMapping("/{contractId}/guarantee/deposit")
    public ResponseEntity<Void> depositGuarantee(@PathVariable String contractId) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{contractId}/guarantee/release")
    public ResponseEntity<Void> releaseGuarantee(@PathVariable String contractId) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{contractId}/guarantee/claim")
    public ResponseEntity<Void> claimGuarantee(@PathVariable String contractId) {
        return ResponseEntity.ok().build();
    }
}

