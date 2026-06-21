package com.thecircle.contracts.controllers;

import com.thecircle.contracts.dto.PaymentDto;
import com.thecircle.contracts.dto.PaymentRequestDto;
import com.thecircle.contracts.security.JwtAuthService;
import com.thecircle.contracts.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Simulated payment endpoints scoped to a contract. The card payload is
 * validated for structure only — no PSP is contacted, no PAN is stored.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtAuthService jwtAuthService;

    public PaymentController(PaymentService paymentService, JwtAuthService jwtAuthService) {
        this.paymentService = paymentService;
        this.jwtAuthService = jwtAuthService;
    }

    @PostMapping
    public ResponseEntity<PaymentDto> pay(@PathVariable String contractId,
                                          @Valid @RequestBody PaymentRequestDto request,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String callerId = jwtAuthService.requireUserId(authHeader);
        PaymentDto dto = paymentService.pay(contractId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<PaymentDto>> list(@PathVariable String contractId,
                                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String callerId = jwtAuthService.requireUserId(authHeader);
        return ResponseEntity.ok(paymentService.listForContract(contractId, callerId));
    }
}
