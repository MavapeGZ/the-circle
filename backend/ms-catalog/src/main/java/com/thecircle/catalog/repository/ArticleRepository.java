package com.thecircle.catalog.repository;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ProductType;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends ElasticsearchRepository<Article, String> {

    List<Article> findByAuthorId(Long authorId);

    // Extra method to find articles by type (DONATION, SYMBOLIC_RENTAL, SYMBOLIC_SALE, or DEMAND)
    Page<Article> findByProductType(ProductType productType, Pageable pageable);

    // --- Catalog-facing queries: all exclude SOLD and DELETED (soft-deleted by
    // author / account removal) articles so they vanish from browsing. Documents
    // with no "status" (legacy) are kept (they are neither SOLD nor DELETED).
    //
    // MAPPING NOTE: each must_not lists both `status` and `status.keyword` to be
    // resilient against indexes that pre-date the Keyword annotation on Article.
    // On legacy indexes status was dynamically mapped as `text` (term-unfriendly)
    // with a `.keyword` sub-field; on fresh indexes status is `keyword` directly
    // and has no sub-field. Listing both ensures one of them matches under any
    // mapping. Extra `term` clauses on a non-existent field are no-ops in OpenSearch.

    @Query("{ \"bool\": { \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findAllNotSold(Pageable pageable);

    @Query("{ \"bool\": { \"filter\": [ { \"term\": { \"zone\": \"?0\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findAllNotSoldByZone(String zone, Pageable pageable);

    @Query("{ \"bool\": { \"filter\": [ { \"term\": { \"type\": \"?0\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findByProductTypeNotSold(ProductType type, Pageable pageable);

    @Query("{ \"bool\": { \"filter\": [ { \"term\": { \"type\": \"?0\" } }, { \"term\": { \"zone\": \"?1\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findByProductTypeNotSoldAndZone(ProductType type, String zone, Pageable pageable);

    @Query("{ \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"fuzziness\": \"AUTO\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findByFuzzySearch(String query, Pageable pageable);

    @Query("{ \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"fuzziness\": \"AUTO\" } } ], \"filter\": [ { \"term\": { \"zone\": \"?1\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findByFuzzySearchAndZone(String query, String zone, Pageable pageable);

    @Query("{ \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"fuzziness\": \"AUTO\" } } ], \"filter\": [ { \"term\": { \"type\": \"?1\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findByFuzzySearchAndType(String query, ProductType type, Pageable pageable);

    @Query("{ \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"fuzziness\": \"AUTO\" } } ], \"filter\": [ { \"term\": { \"type\": \"?1\" } }, { \"term\": { \"zone\": \"?2\" } } ], \"must_not\": [ { \"terms\": { \"status\": [\"SOLD\", \"DELETED\"] } }, { \"terms\": { \"status.keyword\": [\"SOLD\", \"DELETED\"] } } ] } }")
    Page<Article> findByFuzzySearchAndTypeAndZone(String query, ProductType type, String zone, Pageable pageable);
}
