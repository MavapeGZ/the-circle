package com.thecircle.catalog.service;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository repository;

    public Article createArticle(Article article) {
        article.setId(null);
        article.setCreatedAt(LocalDateTime.now());
        return repository.save(article);
    }

    public Iterable<Article> getAllArticles() {
        return repository.findAll();
    }

    public Optional<Article> getArticleById(String id) {
        return repository.findById(id);
    }

    public Article updateArticle(String id, Article updatedData) {
        return repository.findById(id).map(existing -> {
            existing.setTitle(updatedData.getTitle());
            existing.setDescription(updatedData.getDescription());
            existing.setPrice(updatedData.getPrice());
            existing.setCategory(updatedData.getCategory());
            // Do not update creation date or author ID as they should remain unchanged to
            // preserve data integrity
            return repository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Artículo no encontrado con ID: " + id));
    }

    public void deleteArticle(String id) {
        repository.deleteById(id);
    }
}
