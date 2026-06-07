package com.thecircle.gamification.repository;

import com.thecircle.gamification.model.UserPoints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserPointsRepository extends JpaRepository<UserPoints, Long> {

    Optional<UserPoints> findByUserId(Long userId);

    @Query("SELECT up FROM UserPoints up ORDER BY up.totalPoints DESC, up.userId ASC")
    List<UserPoints> findTopByTotalPoints(Pageable pageable);
}
