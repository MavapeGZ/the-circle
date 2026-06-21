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
                new Badge("FIRST_STEPS", "First Steps", "Earned your first 10 points", "🌱", "bronze", 10, null,
                        null),
                new Badge("HELPER", "Helper", "Reached 100 solidarity points", "🤝", "silver", 100, null, null),
                new Badge("CHAMPION", "Champion", "Reached 500 solidarity points", "🏆", "gold", 500, null, null),
                new Badge("FIRST_DONATION", "First Donation", "Donated your first item", "🎁", "bronze", null,
                        EventType.ITEM_DONATED, 1),
                new Badge("SERIAL_DONOR", "Serial Donor", "Donated 5 items", "💝", "silver", null,
                        EventType.ITEM_DONATED, 5),
                new Badge("FIRST_RENTAL", "First Rental", "Rented your first solidarity item", "🏠", "bronze", null,
                        EventType.ITEM_RENTED_SOLIDARITY, 1));

        for (Badge badge : defaults) {
            badgeRepo.findByCode(badge.getCode()).ifPresentOrElse(
                    existing -> {
                        existing.setName(badge.getName());
                        existing.setDescription(badge.getDescription());
                        existing.setIconUrl(badge.getIconUrl());
                        existing.setTier(badge.getTier());
                        existing.setRequiredPoints(badge.getRequiredPoints());
                        existing.setRequiredEventType(badge.getRequiredEventType());
                        existing.setRequiredEventCount(badge.getRequiredEventCount());
                        badgeRepo.save(existing);
                    },
                    () -> badgeRepo.save(badge));
        }
    }
}
