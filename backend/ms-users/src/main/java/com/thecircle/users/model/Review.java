package com.thecircle.users.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A rating one user leaves another after a delivered transaction. Reviews are
 * immutable (no edit/delete) and capped at one per reviewer per contract. The
 * rating is a half-star value between 1.0 and 5.0. Stored on the reviewed
 * (target) user's profile as a received review.
 */
@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reviewer_id", "contract_id"}),
        indexes = @Index(name = "idx_reviews_target", columnList = "target_user_id"))
public class Review {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "contract_id", nullable = false, length = 64)
    private String contractId;

    @Column(nullable = false)
    private double rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Review() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
