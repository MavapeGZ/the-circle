package com.thecircle.gamification.dto;

import com.thecircle.gamification.model.EventType;
import jakarta.validation.constraints.NotNull;

public class AwardEventDto {

    @NotNull
    private Long userId;

    @NotNull
    private EventType eventType;

    private String referenceId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
}
