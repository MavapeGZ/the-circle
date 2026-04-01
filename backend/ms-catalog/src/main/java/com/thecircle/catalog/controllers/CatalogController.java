package com.thecircle.catalog.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    @GetMapping("/api/catalog/health")
    public String health() {
        return "ms-catalog OK";
    }
}

