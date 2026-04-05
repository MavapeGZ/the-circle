package com.thecircle.catalog.controllers;

import com.thecircle.catalog.dto.CatalogItemDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    @GetMapping("/health")
    public String health() {
        return "ms-catalog OK";
    }

    @PostMapping("/items")
    public ResponseEntity<CatalogItemDto> createItem(@RequestBody CatalogItemDto dto) {
        return ResponseEntity.ok(new CatalogItemDto(
            "i1", 
            dto.ownerId(), 
            dto.title(), 
            dto.description(), 
            dto.type(), 
            dto.price(), 
            LocalDateTime.now()
        ));
    }

    @GetMapping("/items")
    public ResponseEntity<List<CatalogItemDto>> searchItems(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        
        return ResponseEntity.ok(List.of(
            new CatalogItemDto("i1", "u1", "Silla de madera", "Silla antigua en buen estado", type != null ? type : "SALE", new BigDecimal("20.0"), LocalDateTime.now())
        ));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<CatalogItemDto> getItemDetails(@PathVariable String itemId) {
        return ResponseEntity.ok(new CatalogItemDto(
            itemId, "u1", "Silla de madera", "Silla antigua en buen estado", "SALE", new BigDecimal("20.0"), LocalDateTime.now()
        ));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CatalogItemDto> updateItem(@PathVariable String itemId, @RequestBody CatalogItemDto dto) {
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable String itemId) {
        return ResponseEntity.noContent().build();
    }
}

