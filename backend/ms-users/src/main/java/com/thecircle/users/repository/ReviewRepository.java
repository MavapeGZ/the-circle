package com.thecircle.users.repository;

import com.thecircle.users.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {

    List<Review> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    boolean existsByReviewerIdAndContractId(Long reviewerId, String contractId);

    long countByTargetUserId(Long targetUserId);

    @Query("select avg(r.rating) from Review r where r.targetUserId = :targetUserId")
    Double averageRating(@Param("targetUserId") Long targetUserId);
}
