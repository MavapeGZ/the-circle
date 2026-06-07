package com.thecircle.catalog.service;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleStatus;
import com.thecircle.catalog.model.ProductType;
import com.thecircle.catalog.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository repository;
    private final double SYMBOLIC_LIMIT_PRICE = 10.0;

    public Article createArticle(Article article) {
        article.setId(null);
        article.setCreatedAt(java.time.Instant.now());
        article.setStatus(ArticleStatus.AVAILABLE);
        validateAndAdjustPrice(article);
        return repository.save(article);
    }

    public Iterable<Article> getAllArticles() {
        // Hide SOLD articles from browsing; keep everything else (incl. legacy nulls).
        List<Article> visible = new ArrayList<>();
        repository.findAll().forEach(a -> {
            if (a.getStatus() != ArticleStatus.SOLD) visible.add(a);
        });
        return visible;
    }

    /** Sets the availability status. Used by ms-contracts as contracts progress. */
    public Article updateStatus(String id, ArticleStatus status) {
        return repository.findById(id).map(a -> {
            a.setStatus(status);
            return repository.save(a);
        }).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Article not found with ID: " + id));
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
            existing.setProductType(updatedData.getProductType());
            if (updatedData.getImageBase64() != null) {
                existing.setImageBase64(updatedData.getImageBase64());
            }
            // Enforce price rules on update too (e.g. donations/demands must stay free)
            validateAndAdjustPrice(existing);
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
        if (article.getPrice() == null) {
            article.setPrice(0.0);
        } else if (article.getPrice() < 0.0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }

        if (article.getProductType() == ProductType.DONATION
                || article.getProductType() == ProductType.DEMAND) {
            article.setPrice(0.0);
        } else if (article.getProductType() == ProductType.SYMBOLIC_SALE
                || article.getProductType() == ProductType.SYMBOLIC_RENTAL) {
            enforceSymbolicCap(article);
        }
    }

    private void enforceSymbolicCap(Article article) {

        if (article.getPrice() > SYMBOLIC_LIMIT_PRICE) {
            article.setPrice(SYMBOLIC_LIMIT_PRICE);
        }
    }

    public Page<Article> searchArticles(String query, ProductType type, Pageable pageable) {

        // Prove if fronend is sending real query or just empty string with spaces, if
        // so, treat it as no query
        boolean hasQuery = query != null && !query.trim().isEmpty();

        if (!hasQuery && type == null) {
            // Case 1: Initial empty search, return all non-sold articles
            return repository.findAllNotSold(pageable);
        } else if (!hasQuery) {
            // Case 2: Filter by type only
            return repository.findByProductTypeNotSold(type, pageable);
        } else if (type == null) {
            // Case 3: Only text in search, no type filter (normal multi-match search)
            return repository.findByFuzzySearch(query, pageable);
        } else {
            // Case 4: Both text and type filter (multi-match search with type filter)
            return repository.findByFuzzySearchAndType(query, type, pageable);
        }
    }
}
