package com.thecircle.users.repository;

import com.thecircle.users.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {

    List<Review> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    boolean existsByReviewerIdAndContractId(Long reviewerId, String contractId);

    long countByTargetUserId(Long targetUserId);

    @Query("select avg(r.rating) from Review r where r.targetUserId = :targetUserId")
    Double averageRating(@Param("targetUserId") Long targetUserId);

    // Aggregated stats for many users in one query: [targetUserId, avg, count].
    // Used to avoid an N+1 when building a batch of public profiles.
    @Query("select r.targetUserId, avg(r.rating), count(r) from Review r "
            + "where r.targetUserId in :targetUserIds group by r.targetUserId")
    List<Object[]> aggregateForTargets(@Param("targetUserIds") Collection<Long> targetUserIds);

    // Removes reviews authored by or addressed to a user (account deletion cleanup).
    void deleteByReviewerIdOrTargetUserId(Long reviewerId, Long targetUserId);
}
