package com.thecircle.catalog.controllers;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService service;

    // 1. Create (Register)
    @PostMapping
    public ResponseEntity<Article> create(@RequestBody Article article) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createArticle(article));
    }

    // 2. Read all
    @GetMapping
    public ResponseEntity<Iterable<Article>> getAll() {
        return ResponseEntity.ok(service.getAllArticles());
    }

    // 3. Read one specific
    @GetMapping("/{id}")
    public ResponseEntity<Article> getById(@PathVariable String id) {
        return service.getArticleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 4. Update
    @PutMapping("/{id}")
    public ResponseEntity<Article> update(@PathVariable String id, @RequestBody Article article) {
        try {
            return ResponseEntity.ok(service.updateArticle(id, article));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 5. Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteArticle(id);
        return ResponseEntity.noContent().build();
    }
}
