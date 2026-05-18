package com.thecircle.gamification.repository;

import com.thecircle.gamification.model.EventType;
import com.thecircle.gamification.model.PointTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserIdAndEventType(Long userId, EventType eventType);
    boolean existsByUserIdAndEventTypeAndReferenceId(Long userId, EventType eventType, String referenceId);
}
