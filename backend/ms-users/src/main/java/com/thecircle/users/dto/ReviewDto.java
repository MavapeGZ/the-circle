package com.thecircle.users.dto;

import java.time.LocalDateTime;

public record ReviewDto(
    String id,
    String reviewerId,
    String targetUserId,
    String contractId,
    Integer rating,
    String comment,
    LocalDateTime createdAt
) {}
