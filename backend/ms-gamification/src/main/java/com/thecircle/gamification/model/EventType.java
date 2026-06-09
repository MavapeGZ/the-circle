package com.thecircle.gamification.model;

public enum EventType {
    ITEM_DONATED(50),
    ITEM_RENTED_SOLIDARITY(30),
    CONTRACT_SIGNED(20),
    REVIEW_RECEIVED(10),
    PROFILE_COMPLETED(25);

    private final int points;

    EventType(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
