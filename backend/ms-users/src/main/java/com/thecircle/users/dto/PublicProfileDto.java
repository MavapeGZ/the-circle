package com.thecircle.users.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PublicProfileDto(
        Long id,
        String displayName,
        String avatarUrl,
        String approximateZone,
        LocalDateTime memberSince,
        int points,
        List<PublicBadgeDto> badges,
        // Average of received reviews (null when the user has none) and how many.
        Double reviewAverage,
        long reviewCount) {
}
