package com.thecircle.catalog.repository;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ProductType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends ElasticsearchRepository<Article, String> {

    // Extra method to find articles by type (DONATION, SYMBOLIC_RENTAL, SYMBOLIC_SALE, or DEMAND)
    Page<Article> findByProductType(ProductType productType, Pageable pageable);

    @Query("{ \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"fuzziness\": \"AUTO\" } } ] } }")
    Page<Article> findByFuzzySearch(String query, Pageable pageable);

    @Query("{ \"bool\": { \"must\": [ { \"multi_match\": { \"query\": \"?0\", \"fields\": [\"title\", \"description\"], \"fuzziness\": \"AUTO\" } } ], \"filter\": [ { \"term\": { \"type\": \"?1\" } } ] } }")
    Page<Article> findByFuzzySearchAndType(String query, ProductType type, Pageable pageable);
}
