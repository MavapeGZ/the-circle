package com.thecircle.catalog.service;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleType;
import com.thecircle.catalog.model.TransactionMode;
import com.thecircle.catalog.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository repository;

    public Article createArticle(Article article) {
        article.setId(null);
        article.setCreatedAt(java.time.Instant.now());
        validateAndAdjustPrice(article);
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
        }).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Article not found with ID: " + id));
    }

    public void deleteArticle(String id) {
        repository.deleteById(id);
    }

    private void validateAndAdjustPrice(Article article) {
        if (article.getTransactionMode() == TransactionMode.DONATE
                || article.getTransactionMode() == TransactionMode.GIFT) {
            article.setPrice(0.0);
        } else {
            if (article.getPrice() == null) {
                article.setPrice(0.0);
            }
        }
    }

    public Page<Article> searchArticles(String query, ArticleType type, Pageable pageable) {

        // Prove if fronend is sending real query or just empty string with spaces, if
        // so, treat it as no query
        boolean hasQuery = query != null && !query.trim().isEmpty();

        if (!hasQuery && type == null) {
            // Case 1: Initial empty search, return all articles with pagination
            return repository.findAll(pageable);
        } else if (!hasQuery) {
            // Case 2: Filter by type only
            return repository.findByType(type, pageable);
        } else if (type == null) {
            // Case 3: Only text in search, no type filter (normal multi-match search)
            return repository.findByFuzzySearch(query, pageable);
        } else {
            // Case 4: Both text and type filter (multi-match search with type filter)
            return repository.findByFuzzySearchAndType(query, type, pageable);
        }
    }
}
