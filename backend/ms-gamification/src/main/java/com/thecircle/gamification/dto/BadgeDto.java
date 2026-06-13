package com.thecircle.gamification.dto;

import com.thecircle.gamification.model.Badge;
import com.thecircle.gamification.model.UserBadge;

import java.time.LocalDateTime;

public class BadgeDto {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String tier;
    private LocalDateTime earnedAt;

    public static BadgeDto from(Badge badge) {
        BadgeDto dto = new BadgeDto();
        dto.id = badge.getId();
        dto.code = badge.getCode();
        dto.name = badge.getName();
        dto.description = badge.getDescription();
        dto.iconUrl = badge.getIconUrl();
        dto.tier = resolveTier(badge.getTier(), badge.getRequiredPoints(), badge.getRequiredEventCount());
        return dto;
    }

    public static BadgeDto from(UserBadge userBadge) {
        BadgeDto dto = from(userBadge.getBadge());
        dto.earnedAt = userBadge.getAwardedAt();
        return dto;
    }

    private static String resolveTier(String tier, Integer requiredPoints, Integer requiredEventCount) {
        if (tier != null && !tier.isBlank()) {
            return tier;
        }
        if (requiredPoints != null) {
            if (requiredPoints >= 100) return "gold";
            if (requiredPoints >= 50) return "silver";
            return "bronze";
        }
        if (requiredEventCount != null) {
            if (requiredEventCount >= 5) return "gold";
            if (requiredEventCount >= 3) return "silver";
            return "bronze";
        }
        return "standard";
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIconUrl() { return iconUrl; }
    public String getTier() { return tier; }
    public LocalDateTime getEarnedAt() { return earnedAt; }
}
