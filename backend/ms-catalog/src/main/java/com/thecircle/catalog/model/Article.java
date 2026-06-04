package com.thecircle.catalog.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;

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
    private Double price; // Symbolic amount for SELL/RENT; 0.0 for donations/demands

    @Field(type = FieldType.Keyword, name = "rental_time_unit")
    private RentalTimeUnit rentalTimeUnit; // Only set when transactionMode == RENT

    // User ID of the author of the article. This is not a reference to a User
    // document, just a simple field to store the ID.
    @Field(type = FieldType.Long, name = "author_id")
    private Long authorId;

    // NOTE: changing this annotation will NOT update the mapping for deployments
    // where the "articles" index already exists. createIndex = true only creates
    // missing indexes. To apply a different date mapping in production you must
    // perform a migration/reindex so the new mapping takes effect.
    @Field(type = FieldType.Date, format = DateFormat.strict_date_optional_time_nanos, name = "created_at")
    private java.time.Instant createdAt;

    @Field(type = FieldType.Text, name = "image_base64", index = false)
    private String imageBase64;
}
