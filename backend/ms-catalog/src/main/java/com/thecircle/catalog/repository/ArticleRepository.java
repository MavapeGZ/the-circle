package com.thecircle.catalog.repository;

import com.thecircle.catalog.model.Article;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends ElasticsearchRepository<Article, String> {
    
    // Extra method to find articles by type (OFFER or DEMAND)
    List<Article> findByType(String type);
}
