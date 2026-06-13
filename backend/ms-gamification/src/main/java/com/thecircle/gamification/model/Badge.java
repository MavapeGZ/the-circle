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

    // Currently holds an emoji glyph, not a URL. A real asset URL is planned
    // but not yet decided; the field name is kept for that future contract.
    private String iconUrl;

    private String tier;

    // Points threshold (null = not points-based)
    private Integer requiredPoints;

    // Event trigger (null = not event-based)
    @Enumerated(EnumType.STRING)
    private EventType requiredEventType;

    // How many times the event must occur (null = not event-based)
    private Integer requiredEventCount;

    public Badge(String code, String name, String description, String iconUrl, String tier,
                 Integer requiredPoints, EventType requiredEventType, Integer requiredEventCount) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.iconUrl = iconUrl;
        this.tier = tier;
        this.requiredPoints = requiredPoints;
        this.requiredEventType = requiredEventType;
        this.requiredEventCount = requiredEventCount;
    }
}
