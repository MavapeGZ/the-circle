package com.thecircle.gamification.service;

import com.thecircle.gamification.model.Badge;
import com.thecircle.gamification.model.EventType;
import com.thecircle.gamification.repository.BadgeRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class BadgeDataInitializer {

    private final BadgeRepository badgeRepo;

    public BadgeDataInitializer(BadgeRepository badgeRepo) {
        this.badgeRepo = badgeRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        List<Badge> defaults = List.of(
            new Badge("FIRST_STEPS",     "Primeros pasos",          "Ganaste tus primeros 10 puntos",     "🌱", 10,   null,                          null),
            new Badge("HELPER",          "Colaborador",             "Alcanzaste 100 puntos solidarios",   "🤝", 100,  null,                          null),
            new Badge("CHAMPION",        "Campeón solidario",       "Alcanzaste 500 puntos solidarios",   "🏆", 500,  null,                          null),
            new Badge("FIRST_DONATION",  "Primera donación",        "Donaste tu primer artículo",         "🎁", null, EventType.ITEM_DONATED,         1),
            new Badge("SERIAL_DONOR",    "Donador habitual",        "Donaste 5 artículos",                "💝", null, EventType.ITEM_DONATED,         5),
            new Badge("FIRST_RENTAL",    "Primer alquiler solidario","Tu primer alquiler solidario",      "🏠", null, EventType.ITEM_RENTED_SOLIDARITY,1)
        );

        for (Badge badge : defaults) {
            badgeRepo.findByCode(badge.getCode()).ifPresentOrElse(
                    existing -> {
                        existing.setName(badge.getName());
                        existing.setDescription(badge.getDescription());
                        existing.setIconUrl(badge.getIconUrl());
                        existing.setRequiredPoints(badge.getRequiredPoints());
                        existing.setRequiredEventType(badge.getRequiredEventType());
                        existing.setRequiredEventCount(badge.getRequiredEventCount());
                        badgeRepo.save(existing);
                    },
                    () -> badgeRepo.save(badge));
        }
    }
}
