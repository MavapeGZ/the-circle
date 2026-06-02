package com.thecircle.catalog.controllers;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleType;
import com.thecircle.catalog.service.ArticleService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/catalog/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService service;

    @PostMapping
    public ResponseEntity<Article> create(
            @RequestBody Article article,
            @RequestHeader("Authorization") String authHeader) {

        try {
            // 1. Retrieve the JWT token from the Authorization header (format: "Bearer
            // <token>")
            String token = authHeader.substring(7);

            // 2. A JWT token has three parts: header, payload, and signature. Payload is
            // the second part
            String[] chunks = token.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));

            // 3. Convert the payload JSON string into a Map to extract claims
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> claims = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });

            // 4. Retrieve the userId claim from the token and set it as the authorId of the
            // article
            Long userId = Long.valueOf(claims.get("userId").toString());
            article.setAuthorId(userId);

            // 5. Save the article using the service and return the created article in the
            // response
            return ResponseEntity.status(HttpStatus.CREATED).body(service.createArticle(article));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    // Read all
    @GetMapping
    public ResponseEntity<Iterable<Article>> getAll() {
        return ResponseEntity.ok(service.getAllArticles());
    }

    // Read one specific
    @GetMapping("/{id}")
    public ResponseEntity<Article> getById(@PathVariable String id) {
        return service.getArticleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Search with optional type filter
    @GetMapping("/search")
    public ResponseEntity<Page<Article>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ArticleType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int maxSize = Math.min(size, 100);
        int maxResultWindow = 10000;
        if ((long) page * maxSize >= maxResultWindow) {
            return ResponseEntity.badRequest().build();
        }
        Pageable pageable = PageRequest.of(page, maxSize);
        return ResponseEntity.ok(service.searchArticles(q, type, pageable));
    }

    // Update
    @PutMapping("/{id}")
    public ResponseEntity<Article> update(@PathVariable String id, @RequestBody Article article) {
        if (service.getArticleById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service.updateArticle(id, article));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteArticle(id);
        return ResponseEntity.noContent().build();
    }
}
