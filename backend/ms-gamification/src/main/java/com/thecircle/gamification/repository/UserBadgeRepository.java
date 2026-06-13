package com.thecircle.gamification.repository;

import com.thecircle.gamification.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserId(Long userId);
    List<UserBadge> findByUserIdIn(Collection<Long> userIds);
}
