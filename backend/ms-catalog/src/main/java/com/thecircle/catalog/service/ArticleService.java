package com.thecircle.catalog.service;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleStatus;
import com.thecircle.catalog.model.ProductType;
import com.thecircle.catalog.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository repository;
    private final com.thecircle.catalog.i18n.Messages messages;
    private final double SYMBOLIC_LIMIT_PRICE = 10.0;
    private static final double GUARANTEE_LIMIT = 20.0;
    // Page size used to drain findAllNotSold below. Bounds per-request memory and
    // stays under OpenSearch's default 10k result window per page.
    private static final int SCAN_PAGE_SIZE = 500;

    public Article createArticle(Article article) {
        article.setId(null);
        article.setCreatedAt(java.time.Instant.now());
        article.setStatus(ArticleStatus.AVAILABLE);
        validateAndAdjustPrice(article);
        validateGuaranteeAmount(article);
        return repository.save(article);
    }

    public Iterable<Article> getAllArticles() {
        // Hide SOLD and DELETED articles from browsing; keep everything else (incl.
        // legacy nulls). Both are excluded server-side (terms query) instead of pulling
        // the whole index into memory and filtering here. Pages are drained so the
        // result stays complete.
        List<Article> visible = new ArrayList<>();
        int page = 0;
        Page<Article> current;
        do {
            current = repository.findAllNotSold(PageRequest.of(page++, SCAN_PAGE_SIZE));
            visible.addAll(current.getContent());
        } while (current.hasNext());
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
            existing.setGuaranteeAmount(updatedData.getGuaranteeAmount());
            if (updatedData.getImageBase64() != null) {
                existing.setImageBase64(updatedData.getImageBase64());
            }
            // Enforce price rules on update too (e.g. donations/demands must stay free)
            validateAndAdjustPrice(existing);
            validateGuaranteeAmount(existing);
            // Do not update creation date or author ID as they should remain unchanged to
            // preserve data integrity
            return repository.save(existing);
        }).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Article not found with ID: " + id));
    }

    public void deleteArticle(String id) {
        repository.deleteById(id);
    }

    public void removeArticlesByAuthorId(Long authorId) {
        List<Article> userArticles = repository.findByAuthorId(authorId);

        if (userArticles.isEmpty()) {
            log.info("No articles found for unsubscribed user {}", authorId);
            return;
        }

        for (Article article : userArticles) {
            article.setStatus(ArticleStatus.DELETED);
        }

        repository.saveAll(userArticles);
        log.info("Soft-deleted {} articles in OpenSearch for unsubscribed user {}", userArticles.size(), authorId);
    }

    private void validateAndAdjustPrice(Article article) {
        if (article.getPrice() == null) {
            article.setPrice(0.0);
        } else if (article.getPrice() < 0.0) {
            throw new IllegalArgumentException(messages.get("catalog.price.negative"));
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

    /**
     * Enforces the security deposit contract: required and 0 < amount ≤ 20€ for
     * rentals; forbidden for any other product type. The cap is hardcoded (not
     * configurable) so it matches the legal/UX promise made to users at listing
     * time and cannot drift via property changes after items are published.
     */
    private void validateGuaranteeAmount(Article article) {
        Double amount = article.getGuaranteeAmount();
        if (article.getProductType() == ProductType.SYMBOLIC_RENTAL) {
            if (amount == null || amount <= 0.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("catalog.guarantee.required"));
            }
            if (amount > GUARANTEE_LIMIT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("catalog.guarantee.max", GUARANTEE_LIMIT));
            }
        } else if (amount != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messages.get("catalog.guarantee.onlyRental"));
        }
    }

    public Page<Article> searchArticles(String query, ProductType type, String zone, Pageable pageable) {

        // Prove if fronend is sending real query or just empty string with spaces, if
        // so, treat it as no query
        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasZone = zone != null && !zone.trim().isEmpty();
        String normalizedZone = hasZone ? zone.trim().toUpperCase() : null;
        if (!hasQuery && type == null && !hasZone) {
            // Case 1: Initial empty search, return all non-sold articles
            return repository.findAllNotSold(pageable);
        } else if (!hasQuery && type != null && !hasZone) {
            // Case 2: Filter by type only
            return repository.findByProductTypeNotSold(type, pageable);
        } else if (hasQuery && type == null && !hasZone) {
            // Case 3: Only text in search, no type filter (normal multi-match search)
            return repository.findByFuzzySearch(query, pageable);
        } else if (hasQuery && type != null && !hasZone) {
            // Case 4: Both text and type filter (multi-match search with type filter)
            return repository.findByFuzzySearchAndType(query, type, pageable);
        } else if (!hasQuery && type == null) {
            return repository.findAllNotSoldByZone(normalizedZone, pageable);
        } else if (!hasQuery) {
            return repository.findByProductTypeNotSoldAndZone(type, normalizedZone, pageable);
        } else if (type == null) {
            return repository.findByFuzzySearchAndZone(query, normalizedZone, pageable);
        } else {
            return repository.findByFuzzySearchAndTypeAndZone(query, type, normalizedZone, pageable);
        }
    }
}
