package com.thecircle.users.dto;

import java.time.LocalDateTime;

public record PublicBadgeDto(
        String code,
        String name,
        String description,
        String iconUrl,
        String tier,
        LocalDateTime earnedAt) {
}