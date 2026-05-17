package com.thecircle.gamification.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_points")
@Getter
@Setter
@NoArgsConstructor
public class UserPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private int totalPoints;

    private LocalDateTime updatedAt;

    public UserPoints(Long userId, int totalPoints) {
        this.userId = userId;
        this.totalPoints = totalPoints;
        this.updatedAt = LocalDateTime.now();
    }
}
