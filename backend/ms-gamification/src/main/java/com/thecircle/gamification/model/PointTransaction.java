package com.thecircle.gamification.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_point_tx_user_event_ref",
                columnNames = {"user_id", "event_type", "reference_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    private String referenceId;

    private LocalDateTime createdAt;

    public PointTransaction(Long userId, int points, EventType eventType, String referenceId) {
        this.userId = userId;
        this.points = points;
        this.eventType = eventType;
        this.referenceId = referenceId;
        this.createdAt = LocalDateTime.now();
    }
}
