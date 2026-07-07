package com.thecircle.catalog.service;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleStatus;
import com.thecircle.catalog.model.ProductType;
import com.thecircle.catalog.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Business-rule unit tests for {@link ArticleService}, exercising the article
 * lifecycle (create / update / search / soft-delete) against a mocked
 * {@link ArticleRepository} so the rules are validated in isolation from
 * OpenSearch (issue #-, doc §4.6).
 *
 * <p>Conventions match the existing catalog tests: JUnit 5 + Mockito + AssertJ,
 * no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository repository;

    private ArticleService service;

    // Use a real MessageSource-backed Messages so assertions can check the actual
    // (English) copy resolved from the i18n bundle, matching production behaviour.
    @BeforeEach
    void setUpService() {
        // Pin the request locale to English so message assertions match the
        // English bundle regardless of the machine's default locale.
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        service = new ArticleService(repository, new com.thecircle.catalog.i18n.Messages(source));
    }

    /** {@code save} echoes back the (already mutated) argument, as OpenSearch would. */
    private void echoSave() {
        when(repository.save(any(Article.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Article article(ProductType type, Double price, Double guarantee) {
        return Article.builder()
                .title("An item")
                .productType(type)
                .price(price)
                .guaranteeAmount(guarantee)
                .build();
    }

    @Nested
    class PriceRules {

        @Test
        void donationForcesPriceToZeroEvenWhenAPriceIsGiven() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.DONATION, 25.0, null));
            assertThat(saved.getPrice()).isZero();
        }

        @Test
        void demandForcesPriceToZero() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.DEMAND, 12.5, null));
            assertThat(saved.getPrice()).isZero();
        }

        @Test
        void nullPriceIsNormalizedToZero() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.SYMBOLIC_SALE, null, null));
            assertThat(saved.getPrice()).isZero();
        }

        @Test
        void negativePriceIsRejected() {
            assertThatThrownBy(() -> service.createArticle(article(ProductType.SYMBOLIC_SALE, -1.0, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Price cannot be negative");
            verify(repository, never()).save(any());
        }

        @Test
        void symbolicSaleAboveCapIsClampedToTen() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.SYMBOLIC_SALE, 50.0, null));
            assertThat(saved.getPrice()).isEqualTo(10.0);
        }

        @Test
        void symbolicSaleWithinCapIsKept() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.SYMBOLIC_SALE, 7.5, null));
            assertThat(saved.getPrice()).isEqualTo(7.5);
        }

        @Test
        void symbolicRentalAboveCapIsClampedToTen() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.SYMBOLIC_RENTAL, 999.0, 5.0));
            assertThat(saved.getPrice()).isEqualTo(10.0);
        }
    }

    @Nested
    class GuaranteeRules {

        @Test
        void rentalRequiresAGuarantee() {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> service.createArticle(article(ProductType.SYMBOLIC_RENTAL, 5.0, null)))
                    .withMessageContaining("guaranteeAmount is required");
            verify(repository, never()).save(any());
        }

        @Test
        void rentalGuaranteeMustBePositive() {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> service.createArticle(article(ProductType.SYMBOLIC_RENTAL, 5.0, 0.0)))
                    .withMessageContaining("must be greater than 0");
        }

        @Test
        void rentalGuaranteeAboveTwentyIsRejected() {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> service.createArticle(article(ProductType.SYMBOLIC_RENTAL, 5.0, 25.0)))
                    .withMessageContaining("cannot exceed");
        }

        @Test
        void validRentalGuaranteeIsAccepted() {
            echoSave();
            Article saved = service.createArticle(article(ProductType.SYMBOLIC_RENTAL, 5.0, 20.0));
            assertThat(saved.getGuaranteeAmount()).isEqualTo(20.0);
        }

        @Test
        void guaranteeOnNonRentalIsRejected() {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> service.createArticle(article(ProductType.SYMBOLIC_SALE, 5.0, 10.0)))
                    .withMessageContaining("only allowed for SYMBOLIC_RENTAL");
        }
    }

    @Nested
    class CreateDefaults {

        @Test
        void newArticleStartsAvailableWithFreshTimestampAndNoClientId() {
            echoSave();
            Article input = article(ProductType.SYMBOLIC_SALE, 5.0, null);
            input.setId("client-supplied-id");
            Article saved = service.createArticle(input);
            assertThat(saved.getId()).isNull();
            assertThat(saved.getStatus()).isEqualTo(ArticleStatus.AVAILABLE);
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    class UpdateStatus {

        @Test
        void updateStatusPersistsTheNewStatus() {
            Article existing = article(ProductType.SYMBOLIC_SALE, 5.0, null);
            existing.setStatus(ArticleStatus.AVAILABLE);
            when(repository.findById("a1")).thenReturn(Optional.of(existing));
            echoSave();

            Article result = service.updateStatus("a1", ArticleStatus.SOLD);

            assertThat(result.getStatus()).isEqualTo(ArticleStatus.SOLD);
        }

        @Test
        void updateStatusOnMissingArticleReturnsNotFound() {
            when(repository.findById("missing")).thenReturn(Optional.empty());
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> service.updateStatus("missing", ArticleStatus.SOLD))
                    .matches(e -> e.getStatusCode().value() == 404);
        }
    }

    @Nested
    class UpdateArticle {

        @Test
        void updateReappliesPriceRulesSoDonationsStayFree() {
            Article existing = article(ProductType.DONATION, 0.0, null);
            existing.setStatus(ArticleStatus.AVAILABLE);
            when(repository.findById("a1")).thenReturn(Optional.of(existing));
            echoSave();

            Article incoming = article(ProductType.DONATION, 99.0, null);
            Article result = service.updateArticle("a1", incoming);

            assertThat(result.getPrice()).isZero();
        }

        @Test
        void updateOnMissingArticleReturnsNotFound() {
            when(repository.findById("missing")).thenReturn(Optional.empty());
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> service.updateArticle("missing", article(ProductType.SYMBOLIC_SALE, 5.0, null)))
                    .matches(e -> e.getStatusCode().value() == 404);
        }
    }

    @Nested
    class Browsing {

        @Test
        void getAllArticlesDrainsEveryPageOfNonSoldResults() {
            Article a = article(ProductType.SYMBOLIC_SALE, 1.0, null);
            Article b = article(ProductType.DONATION, 0.0, null);
            // Page 0 reports a next page (total > page size), page 1 is the last.
            Page<Article> page0 = new PageImpl<>(List.of(a), PageRequest.of(0, 500), 501);
            Page<Article> page1 = new PageImpl<>(List.of(b), PageRequest.of(1, 500), 501);
            when(repository.findAllNotSold(any(Pageable.class))).thenReturn(page0, page1);

            List<Article> visible = (List<Article>) service.getAllArticles();

            assertThat(visible).containsExactly(a, b);
            verify(repository, never()).findAll();
        }
    }

    @Nested
    class Search {

        private final Page<Article> sentinel = new PageImpl<>(List.of());
        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        void noFiltersReturnsAllNonSoldArticles() {
            when(repository.findAllNotSold(pageable)).thenReturn(sentinel);
            assertThat(service.searchArticles("  ", null, null, pageable)).isSameAs(sentinel);
        }

        @Test
        void typeOnlyFiltersByProductType() {
            when(repository.findByProductTypeNotSold(ProductType.DONATION, pageable)).thenReturn(sentinel);
            assertThat(service.searchArticles(null, ProductType.DONATION, null, pageable)).isSameAs(sentinel);
        }

        @Test
        void queryOnlyRunsFuzzySearch() {
            when(repository.findByFuzzySearch("bike", pageable)).thenReturn(sentinel);
            assertThat(service.searchArticles("bike", null, null, pageable)).isSameAs(sentinel);
        }

        @Test
        void queryAndTypeRunFuzzySearchWithTypeFilter() {
            when(repository.findByFuzzySearchAndType("bike", ProductType.SYMBOLIC_SALE, pageable)).thenReturn(sentinel);
            assertThat(service.searchArticles("bike", ProductType.SYMBOLIC_SALE, null, pageable)).isSameAs(sentinel);
        }

        @Test
        void zoneOnlyFiltersByNormalizedZone() {
            when(repository.findAllNotSoldByZone(eq("MADRID"), eq(pageable))).thenReturn(sentinel);
            assertThat(service.searchArticles(null, null, "  madrid ", pageable)).isSameAs(sentinel);
        }

        @Test
        void typeAndZoneFilterByBoth() {
            when(repository.findByProductTypeNotSoldAndZone(eq(ProductType.DONATION), eq("MADRID"), eq(pageable)))
                    .thenReturn(sentinel);
            assertThat(service.searchArticles(null, ProductType.DONATION, "madrid", pageable)).isSameAs(sentinel);
        }

        @Test
        void queryAndZoneRunFuzzySearchWithinZone() {
            when(repository.findByFuzzySearchAndZone(eq("bike"), eq("MADRID"), eq(pageable))).thenReturn(sentinel);
            assertThat(service.searchArticles("bike", null, "madrid", pageable)).isSameAs(sentinel);
        }

        @Test
        void allThreeFiltersCombine() {
            when(repository.findByFuzzySearchAndTypeAndZone(
                    eq("bike"), eq(ProductType.SYMBOLIC_SALE), eq("MADRID"), eq(pageable))).thenReturn(sentinel);
            assertThat(service.searchArticles("bike", ProductType.SYMBOLIC_SALE, "madrid", pageable))
                    .isSameAs(sentinel);
        }

        @Test
        void zoneIsTrimmedAndUppercasedBeforeQuerying() {
            ArgumentCaptor<String> zoneCaptor = ArgumentCaptor.forClass(String.class);
            when(repository.findAllNotSoldByZone(zoneCaptor.capture(), eq(pageable))).thenReturn(sentinel);

            service.searchArticles(null, null, "  bcn ", pageable);

            assertThat(zoneCaptor.getValue()).isEqualTo("BCN");
        }
    }

    @Nested
    class SoftDeleteByAuthor {

        @Test
        void removeMarksEveryAuthorArticleAsDeleted() {
            Article a = article(ProductType.SYMBOLIC_SALE, 1.0, null);
            Article b = article(ProductType.DONATION, 0.0, null);
            when(repository.findByAuthorId(7L)).thenReturn(List.of(a, b));

            service.removeArticlesByAuthorId(7L);

            assertThat(a.getStatus()).isEqualTo(ArticleStatus.DELETED);
            assertThat(b.getStatus()).isEqualTo(ArticleStatus.DELETED);
            verify(repository).saveAll(List.of(a, b));
        }

        @Test
        void removeWithNoAuthorArticlesDoesNotTouchTheIndex() {
            when(repository.findByAuthorId(7L)).thenReturn(List.of());

            assertThatCode(() -> service.removeArticlesByAuthorId(7L)).doesNotThrowAnyException();

            verify(repository, never()).saveAll(any());
        }
    }

    @Nested
    class DeleteAndLookup {

        @Test
        void deleteDelegatesToRepository() {
            service.deleteArticle("a1");
            verify(repository).deleteById("a1");
        }

        @Test
        void getByIdReturnsRepositoryResult() {
            Article a = article(ProductType.SYMBOLIC_SALE, 5.0, null);
            when(repository.findById("a1")).thenReturn(Optional.of(a));
            assertThat(service.getArticleById("a1")).contains(a);
        }
    }
}
