package com.thecircle.gamification.dto;

import java.util.List;

public class AwardEventResponseDto {

    private Long userId;
    private int pointsAwarded;
    private int totalPoints;
    private List<BadgeDto> newBadges;

    public AwardEventResponseDto(Long userId, int pointsAwarded, int totalPoints, List<BadgeDto> newBadges) {
        this.userId = userId;
        this.pointsAwarded = pointsAwarded;
        this.totalPoints = totalPoints;
        this.newBadges = newBadges;
    }

    public Long getUserId() { return userId; }
    public int getPointsAwarded() { return pointsAwarded; }
    public int getTotalPoints() { return totalPoints; }
    public List<BadgeDto> getNewBadges() { return newBadges; }
}
