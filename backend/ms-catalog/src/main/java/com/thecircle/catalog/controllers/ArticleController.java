package com.thecircle.catalog.controllers;

import com.thecircle.catalog.client.UsersClient;
import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ProductType;
import com.thecircle.catalog.service.ArticleService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.SecretKey;

@RestController
@RequestMapping("/api/catalog/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService service;
    private final UsersClient usersClient;

    @Value("${jwt.secret}")
    private String secretKey;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Create (Register)
    @PostMapping
    public ResponseEntity<Article> create(
            @RequestBody Article article,
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = authHeader.substring(7);

            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.get("userId").toString());
            UsersClient.PayoutAccount payout = usersClient.getPayoutAccount(userId);
            if (payout != null) {
                article.setZone(payout.zone());
            }
            if (article.getProductType() == ProductType.DONATION
                    || article.getProductType() == ProductType.DEMAND) {
                if (article.getPrice() != null && article.getPrice() > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid price: Donations and demands must be completely free (price = 0.0).");
                }
                article.setPrice(0.0); // Force price to 0 for security and data integrity reasons
            }

            // Paid product types (SALE / RENT) cannot be published until the seller has
            // a payout IBAN on file: the symbolic payment flow needs somewhere to
            // release funds to, and we want the user to fix this before they spend time
            // filling out the listing. 422 + actionable message tells the frontend
            // exactly what is missing so it can redirect to settings.
            if (article.getProductType() == ProductType.SYMBOLIC_SALE
                    || article.getProductType() == ProductType.SYMBOLIC_RENTAL) {
                if (payout == null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Could not verify your payout account right now. Please try again in a moment.");
                }
                if (!payout.hasIban()) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "A payout IBAN is required to publish paid items. Please add one in Settings → Payments before publishing.");
                }
            }

            article.setAuthorId(userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(service.createArticle(article));

        } catch (ResponseStatusException e) {
            // Preserve validation errors (e.g. invalid price -> 400), do not mask as 401
            throw e;
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
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) String zone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int maxSize = Math.min(size, 100);
        int maxResultWindow = 10000;
        if ((long) page * maxSize >= maxResultWindow) {
            return ResponseEntity.badRequest().build();
        }
        Pageable pageable = PageRequest.of(page, maxSize);
        return ResponseEntity.ok(service.searchArticles(q, productType, zone, pageable));
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
