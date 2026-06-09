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
    private LocalDateTime awardedAt;

    public static BadgeDto from(Badge badge) {
        BadgeDto dto = new BadgeDto();
        dto.id = badge.getId();
        dto.code = badge.getCode();
        dto.name = badge.getName();
        dto.description = badge.getDescription();
        dto.iconUrl = badge.getIconUrl();
        return dto;
    }

    public static BadgeDto from(UserBadge userBadge) {
        BadgeDto dto = from(userBadge.getBadge());
        dto.awardedAt = userBadge.getAwardedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIconUrl() { return iconUrl; }
    public LocalDateTime getAwardedAt() { return awardedAt; }
}
