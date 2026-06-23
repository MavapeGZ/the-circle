package com.thecircle.contracts.dto;

import java.time.LocalDateTime;

/**
 * Conversation as seen by one caller. {@code otherUserId} is the participant who
 * is not the caller, {@code unreadCount} counts messages the caller has not read
 * yet, and {@code lastMessage} is a preview for the inbox list (may be null on a
 * freshly created, empty conversation).
 */
public record ConversationDto(
        String id,
        String articleId,
        String ownerId,
        String initiatorId,
        String otherUserId,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        String lastMessage,
        long unreadCount) {
}
