package com.thecircle.gamification.dto;

import java.util.List;

public class UserSummaryDto {

    private Long userId;
    private int totalPoints;
    private List<BadgeDto> badges;
    private List<PointTransactionDto> recentTransactions;

    public UserSummaryDto(Long userId, int totalPoints, List<BadgeDto> badges, List<PointTransactionDto> recentTransactions) {
        this.userId = userId;
        this.totalPoints = totalPoints;
        this.badges = badges;
        this.recentTransactions = recentTransactions;
    }

    public Long getUserId() { return userId; }
    public int getTotalPoints() { return totalPoints; }
    public List<BadgeDto> getBadges() { return badges; }
    public List<PointTransactionDto> getRecentTransactions() { return recentTransactions; }
}
