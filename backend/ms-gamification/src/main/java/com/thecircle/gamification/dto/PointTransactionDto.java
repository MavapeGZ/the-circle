package com.thecircle.gamification.dto;

import com.thecircle.gamification.model.EventType;
import com.thecircle.gamification.model.PointTransaction;

import java.time.LocalDateTime;

public class PointTransactionDto {

    private Long id;
    private int points;
    private EventType eventType;
    private String referenceId;
    private LocalDateTime createdAt;

    public static PointTransactionDto from(PointTransaction tx) {
        PointTransactionDto dto = new PointTransactionDto();
        dto.id = tx.getId();
        dto.points = tx.getPoints();
        dto.eventType = tx.getEventType();
        dto.referenceId = tx.getReferenceId();
        dto.createdAt = tx.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public int getPoints() { return points; }
    public EventType getEventType() { return eventType; }
    public String getReferenceId() { return referenceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
