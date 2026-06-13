package com.thecircle.gamification.service;

import com.thecircle.gamification.dto.AwardEventDto;
import com.thecircle.gamification.dto.AwardEventResponseDto;
import com.thecircle.gamification.dto.UserSummaryDto;
import com.thecircle.gamification.model.Badge;
import com.thecircle.gamification.model.EventType;
import com.thecircle.gamification.repository.BadgeRepository;
import com.thecircle.gamification.repository.PointTransactionRepository;
import com.thecircle.gamification.repository.UserBadgeRepository;
import com.thecircle.gamification.repository.UserPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(GamificationService.class)
class GamificationServiceTest {

    @Autowired
    private GamificationService service;

    @Autowired
    private BadgeRepository badgeRepo;

    @Autowired
    private UserPointsRepository userPointsRepo;

    @Autowired
    private UserBadgeRepository userBadgeRepo;

    @Autowired
    private PointTransactionRepository transactionRepo;

    private static final Long USER_ID = 1L;
    private static final String BADGE_FIRST_STEPS    = "FIRST_STEPS";
    private static final String BADGE_HELPER         = "HELPER";
    private static final String BADGE_FIRST_DONATION = "FIRST_DONATION";
    private static final String BADGE_SERIAL_DONOR   = "SERIAL_DONOR";
    private static final String CONTRACT_REF_ID      = "contract-abc";

    @BeforeEach
    void seedBadges() {
        badgeRepo.deleteAll();
        badgeRepo.save(new Badge(BADGE_FIRST_STEPS,    "Primeros pasos",   "10 pts",  "🌱", "bronze", 10,   null,                          null));
        badgeRepo.save(new Badge(BADGE_HELPER,          "Colaborador",      "100 pts", "🤝", "gold", 100,  null,                          null));
        badgeRepo.save(new Badge(BADGE_FIRST_DONATION,  "Primera donación", "1 dona",  "🎁", "bronze", null, EventType.ITEM_DONATED,         1));
        badgeRepo.save(new Badge(BADGE_SERIAL_DONOR,    "Donador habitual", "5 donas", "💝", "silver", null, EventType.ITEM_DONATED,         5));
    }

    private AwardEventDto event(EventType type) {
        AwardEventDto dto = new AwardEventDto();
        dto.setUserId(USER_ID);
        dto.setEventType(type);
        return dto;
    }

    @Test
    void processEvent_firstEvent_createsUserPointsAndTransaction() {
        AwardEventResponseDto resp = service.processEvent(event(EventType.REVIEW_RECEIVED));

        assertEquals(USER_ID, resp.getUserId());
        assertEquals(EventType.REVIEW_RECEIVED.getPoints(), resp.getPointsAwarded());
        assertEquals(EventType.REVIEW_RECEIVED.getPoints(), resp.getTotalPoints());
        assertEquals(1, transactionRepo.count());
        assertEquals(1, userPointsRepo.count());
    }

    @Test
    void processEvent_multipleEvents_accumulatesPoints() {
        service.processEvent(event(EventType.REVIEW_RECEIVED));
        AwardEventResponseDto resp = service.processEvent(event(EventType.CONTRACT_SIGNED));

        int expected = EventType.REVIEW_RECEIVED.getPoints() + EventType.CONTRACT_SIGNED.getPoints();
        assertEquals(expected, resp.getTotalPoints());
        assertEquals(2, transactionRepo.count());
    }

    @Test
    void processEvent_reachesPointThreshold_awardsBadge() {
        // FIRST_STEPS requires 10 pts. REVIEW_RECEIVED = 10 pts.
        AwardEventResponseDto resp = service.processEvent(event(EventType.REVIEW_RECEIVED));

        assertEquals(1, resp.getNewBadges().size());
        assertEquals(BADGE_FIRST_STEPS, resp.getNewBadges().get(0).getCode());
        assertEquals(1, userBadgeRepo.count());
    }

    @Test
    void processEvent_badgeAlreadyEarned_doesNotDuplicate() {
        service.processEvent(event(EventType.REVIEW_RECEIVED)); // earns FIRST_STEPS
        AwardEventResponseDto resp = service.processEvent(event(EventType.REVIEW_RECEIVED));

        assertEquals(0, resp.getNewBadges().size());
        assertEquals(1, userBadgeRepo.count()); // still only one badge
    }

    @Test
    void processEvent_firstDonation_awardsDonationBadge() {
        AwardEventResponseDto resp = service.processEvent(event(EventType.ITEM_DONATED));

        assertTrue(resp.getNewBadges().stream().anyMatch(b -> b.getCode().equals(BADGE_FIRST_DONATION)));
    }

    @Test
    void processEvent_fiveDonations_awardsSerialDonorBadge() {
        for (int i = 0; i < 4; i++) {
            service.processEvent(event(EventType.ITEM_DONATED));
        }
        AwardEventResponseDto resp = service.processEvent(event(EventType.ITEM_DONATED));

        assertTrue(resp.getNewBadges().stream().anyMatch(b -> b.getCode().equals(BADGE_SERIAL_DONOR)));
    }

    @Test
    void processEvent_referenceId_persistedInTransaction() {
        AwardEventDto dto = event(EventType.CONTRACT_SIGNED);
        dto.setReferenceId(CONTRACT_REF_ID);
        service.processEvent(dto);

        assertEquals(CONTRACT_REF_ID, transactionRepo.findAll().get(0).getReferenceId());
    }

    @Test
    void getUserSummary_unknownUser_returnsZeroPoints() {
        UserSummaryDto summary = service.getUserSummary(999L);

        assertEquals(999L, summary.getUserId());
        assertEquals(0, summary.getTotalPoints());
        assertTrue(summary.getBadges().isEmpty());
        assertTrue(summary.getRecentTransactions().isEmpty());
    }

    @Test
    void getUserSummary_afterEvents_returnsCorrectData() {
        service.processEvent(event(EventType.ITEM_DONATED));
        service.processEvent(event(EventType.CONTRACT_SIGNED));

        UserSummaryDto summary = service.getUserSummary(USER_ID);

        int expected = EventType.ITEM_DONATED.getPoints() + EventType.CONTRACT_SIGNED.getPoints();
        assertEquals(expected, summary.getTotalPoints());
        assertEquals(2, summary.getRecentTransactions().size());
        assertFalse(summary.getBadges().isEmpty());
    }

    @Test
    void getLeaderboard_multipleUsers_orderedByPoints() {
        AwardEventDto u2 = new AwardEventDto();
        u2.setUserId(2L);
        u2.setEventType(EventType.ITEM_DONATED);

        service.processEvent(event(EventType.REVIEW_RECEIVED)); // user 1: 10 pts
        service.processEvent(u2);                               // user 2: 50 pts

        var leaderboard = service.getLeaderboard();

        assertEquals(2L, leaderboard.get(0).getUserId()); // user 2 leads
        assertEquals(1, leaderboard.get(0).getRank());
        assertEquals(USER_ID, leaderboard.get(1).getUserId());
        assertEquals(2, leaderboard.get(1).getRank());
    }

    @Test
    void getAllBadges_returnsSeededBadges() {
        var badges = service.getAllBadges();
        assertFalse(badges.isEmpty());
        assertTrue(badges.stream().anyMatch(b -> b.getCode().equals(BADGE_FIRST_STEPS)));
        assertTrue(badges.stream().anyMatch(b -> b.getCode().equals(BADGE_SERIAL_DONOR)));
    }

    @Test
    void getUserPoints_unknownUser_returnsZero() {
        var points = service.getUserPoints(999L);
        assertEquals(999L, points.userId());
        assertEquals(0, points.totalPoints());
    }

    @Test
    void getUserSummaries_returnsRequestedUsers() {
        service.processEvent(event(EventType.REVIEW_RECEIVED));
        var summaries = service.getUserSummaries(List.of(USER_ID, 999L));

        assertEquals(2, summaries.size());
        assertEquals(USER_ID, summaries.get(0).getUserId());
        assertEquals(999L, summaries.get(1).getUserId());
        assertFalse(summaries.get(0).getBadges().isEmpty());
        assertTrue(summaries.get(1).getBadges().isEmpty());
    }
}
