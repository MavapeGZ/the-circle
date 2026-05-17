package com.thecircle.gamification.dto;

public class LeaderboardEntryDto {

    private int rank;
    private Long userId;
    private int totalPoints;

    public LeaderboardEntryDto(int rank, Long userId, int totalPoints) {
        this.rank = rank;
        this.userId = userId;
        this.totalPoints = totalPoints;
    }

    public int getRank() { return rank; }
    public Long getUserId() { return userId; }
    public int getTotalPoints() { return totalPoints; }
}
