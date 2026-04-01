package com.thecircle.contracts.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContractsController {
    @GetMapping("/api/contracts/health")
    public String health() {
        return "ms-contracts OK";
    }
}

