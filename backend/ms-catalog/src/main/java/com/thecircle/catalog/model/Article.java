package com.thecircle.catalog.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "articles", createIndex = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    private String id; // OpenSearch generates this automatically

    @Field(type = FieldType.Text, name = "title")
    private String title;

    @Field(type = FieldType.Text, name = "description")
    private String description;

    @Field(type = FieldType.Keyword, name = "type")
    private ArticleType type; // OFFER o DEMAND

    @Field(type = FieldType.Keyword, name = "transaction_mode")
    private TransactionMode transactionMode; // RENT, SELL, DONATE, GIFT

    @Field(type = FieldType.Keyword, name = "category")
    private String category;

    @Field(type = FieldType.Double, name = "price")
    private Double price; // Could be 0.0 for free offers or demands

    // User ID of the author of the article. This is not a reference to a User
    // document, just a simple field to store the ID.
    @Field(type = FieldType.Long, name = "author_id")
    private Long authorId;

    @Field(type = FieldType.Date, name = "created_at")
    private LocalDateTime createdAt;
}
