package com.thecircle.gamification.service;

import com.thecircle.gamification.dto.*;
import com.thecircle.gamification.model.*;
import com.thecircle.gamification.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GamificationService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 10;
    private static final int LEADERBOARD_SIZE = 10;
    private static final int MAX_AWARD_RETRIES = 4;

    private final UserPointsRepository userPointsRepo;
    private final BadgeRepository badgeRepo;
    private final UserBadgeRepository userBadgeRepo;
    private final PointTransactionRepository transactionRepo;

    // Self-reference so the retry loop in processEvent invokes awardEventOnce
    // through the Spring proxy — each attempt then runs in its own transaction.
    private final GamificationService self;

    public GamificationService(UserPointsRepository userPointsRepo,
                                BadgeRepository badgeRepo,
                                UserBadgeRepository userBadgeRepo,
                                PointTransactionRepository transactionRepo,
                                @Lazy GamificationService self) {
        this.userPointsRepo = userPointsRepo;
        this.badgeRepo = badgeRepo;
        this.userBadgeRepo = userBadgeRepo;
        this.transactionRepo = transactionRepo;
        this.self = self;
    }

    /**
     * Awards an event, retrying on concurrent-write conflicts. A conflict can be an
     * optimistic-lock failure on UserPoints or a unique-constraint violation from a
     * racing first-insert / duplicate transaction; on retry the operation observes
     * the committed state and resolves (increment, or idempotent no-op).
     */
    public AwardEventResponseDto processEvent(AwardEventDto dto) {
        int attempt = 0;
        while (true) {
            try {
                return self.awardEventOnce(dto);
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                if (++attempt >= MAX_AWARD_RETRIES) {
                    throw new IllegalStateException(
                            "Unexpected error. Please contact our support team.", ex);
                }
            }
        }
    }

    @Transactional
    public AwardEventResponseDto awardEventOnce(AwardEventDto dto) {
        // Idempotency: a referenced event that was already processed is a no-op.
        if (dto.getReferenceId() != null && transactionRepo.existsByUserIdAndEventTypeAndReferenceId(
                dto.getUserId(), dto.getEventType(), dto.getReferenceId())) {
            int current = userPointsRepo.findByUserId(dto.getUserId())
                    .map(UserPoints::getTotalPoints)
                    .orElse(0);
            return new AwardEventResponseDto(dto.getUserId(), 0, current, List.of());
        }

        int pointsAwarded = dto.getEventType().getPoints();

        UserPoints userPoints = userPointsRepo.findByUserId(dto.getUserId())
                .orElseGet(() -> new UserPoints(dto.getUserId(), 0));
        userPoints.setTotalPoints(userPoints.getTotalPoints() + pointsAwarded);
        userPoints.setUpdatedAt(LocalDateTime.now());
        userPointsRepo.save(userPoints);

        transactionRepo.save(new PointTransaction(dto.getUserId(), pointsAwarded, dto.getEventType(), dto.getReferenceId()));

        List<BadgeDto> newBadges = checkAndAwardBadges(dto.getUserId(), userPoints.getTotalPoints(), dto.getEventType())
                .stream().map(BadgeDto::from).collect(Collectors.toList());

        return new AwardEventResponseDto(dto.getUserId(), pointsAwarded, userPoints.getTotalPoints(), newBadges);
    }

    @Transactional(readOnly = true)
    public UserSummaryDto getUserSummary(Long userId) {
        int totalPoints = userPointsRepo.findByUserId(userId)
                .map(UserPoints::getTotalPoints)
                .orElse(0);

        List<BadgeDto> badges = userBadgeRepo.findByUserId(userId).stream()
                .map(BadgeDto::from)
                .collect(Collectors.toList());

        List<PointTransactionDto> recent = transactionRepo
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT))
                .stream().map(PointTransactionDto::from).collect(Collectors.toList());

        return new UserSummaryDto(userId, totalPoints, badges, recent);
    }

    @Transactional(readOnly = true)
    public UserPointsDto getUserPoints(Long userId) {
    int totalPoints = userPointsRepo.findByUserId(userId)
        .map(UserPoints::getTotalPoints)
        .orElse(0);
    return new UserPointsDto(userId, totalPoints);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getUserSummaries(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
        return List.of();
    }

    List<Long> uniqueUserIds = userIds.stream().distinct().toList();
    var pointsByUserId = userPointsRepo.findByUserIdIn(uniqueUserIds).stream()
        .collect(Collectors.toMap(UserPoints::getUserId, UserPoints::getTotalPoints));

    var badgesByUserId = userBadgeRepo.findByUserIdIn(uniqueUserIds).stream()
        .collect(Collectors.groupingBy(UserBadge::getUserId,
            Collectors.mapping(BadgeDto::from, Collectors.toList())));

    return uniqueUserIds.stream()
        .map(userId -> new UserSummaryDto(
            userId,
            pointsByUserId.getOrDefault(userId, 0),
            badgesByUserId.getOrDefault(userId, List.of()),
            List.of()))
        .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BadgeDto> getUserBadges(Long userId) {
        return userBadgeRepo.findByUserId(userId).stream()
                .map(BadgeDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BadgeDto> getAllBadges() {
        return badgeRepo.findAll().stream()
                .map(BadgeDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getLeaderboard() {
        List<UserPoints> top = userPointsRepo.findTopByTotalPoints(PageRequest.of(0, LEADERBOARD_SIZE));
        List<LeaderboardEntryDto> result = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            UserPoints up = top.get(i);
            result.add(new LeaderboardEntryDto(i + 1, up.getUserId(), up.getTotalPoints()));
        }
        return result;
    }

    private List<Badge> checkAndAwardBadges(Long userId, int totalPoints, EventType eventType) {
        List<Badge> allBadges = badgeRepo.findAll();
        Set<Long> earnedIds = userBadgeRepo.findByUserId(userId).stream()
                .map(ub -> ub.getBadge().getId())
                .collect(Collectors.toSet());

        List<Badge> newlyEarned = new ArrayList<>();
        for (Badge badge : allBadges) {
            if (earnedIds.contains(badge.getId())) continue;

            boolean qualifies = false;

            if (badge.getRequiredPoints() != null && totalPoints >= badge.getRequiredPoints()) {
                qualifies = true;
            }

            if (badge.getRequiredEventType() != null && badge.getRequiredEventType() == eventType
                    && badge.getRequiredEventCount() != null) {
                long count = transactionRepo.countByUserIdAndEventType(userId, eventType);
                if (count >= badge.getRequiredEventCount()) {
                    qualifies = true;
                }
            }

            if (qualifies) {
                userBadgeRepo.save(new UserBadge(userId, badge, LocalDateTime.now()));
                newlyEarned.add(badge);
            }
        }
        return newlyEarned;
    }
}
