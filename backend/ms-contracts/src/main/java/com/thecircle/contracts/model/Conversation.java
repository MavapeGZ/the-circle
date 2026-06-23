package com.thecircle.contracts.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A chat thread about a catalog article between its owner (author) and an
 * initiator (the interested user who started the conversation). A conversation
 * is independent of any contract: it can be opened just to ask questions, and
 * it stays open after a deal is signed so both parties can keep talking until
 * the item is delivered. There is at most one conversation per (article,
 * initiator) pair.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"article_id", "initiator_id"}))
public class Conversation {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "article_id", nullable = false)
    private String articleId;

    // Article author. Resolved from ms-catalog at creation time.
    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    // The user who started the conversation (never the owner).
    @Column(name = "initiator_id", nullable = false)
    private String initiatorId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Bumped on every new message so conversation lists can sort by recency.
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public Conversation() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getInitiatorId() { return initiatorId; }
    public void setInitiatorId(String initiatorId) { this.initiatorId = initiatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    /** True if {@code userId} is the owner or the initiator of this conversation. */
    public boolean isParticipant(String userId) {
        return userId != null && (userId.equals(ownerId) || userId.equals(initiatorId));
    }
}
