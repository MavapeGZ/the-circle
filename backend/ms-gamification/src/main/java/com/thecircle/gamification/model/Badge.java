package com.thecircle.gamification.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "badges")
@Getter
@Setter
@NoArgsConstructor
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;
    private String iconUrl;

    // Points threshold (null = not points-based)
    private Integer requiredPoints;

    // Event trigger (null = not event-based)
    @Enumerated(EnumType.STRING)
    private EventType requiredEventType;

    // How many times the event must occur (null = not event-based)
    private Integer requiredEventCount;

    public Badge(String code, String name, String description, String iconUrl,
                 Integer requiredPoints, EventType requiredEventType, Integer requiredEventCount) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.iconUrl = iconUrl;
        this.requiredPoints = requiredPoints;
        this.requiredEventType = requiredEventType;
        this.requiredEventCount = requiredEventCount;
    }
}
