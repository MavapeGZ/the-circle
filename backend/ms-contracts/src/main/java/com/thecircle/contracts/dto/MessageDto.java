package com.thecircle.contracts.dto;

import java.time.LocalDateTime;

public record MessageDto(
        String id,
        String conversationId,
        String senderId,
        String body,
        LocalDateTime createdAt,
        LocalDateTime readAt) {
}
