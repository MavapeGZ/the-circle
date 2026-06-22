package com.thecircle.users.service;

import com.thecircle.users.dto.ReviewDto;
import com.thecircle.users.model.Review;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.ReviewRepository;
import com.thecircle.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reviews left after a delivered transaction. A review is allowed only when the
 * reviewer and the reviewed user were the two parties of a contract that both
 * confirmed as delivered (verified against ms-contracts), one review per reviewer
 * per contract, no self-reviews. Reviews are immutable.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ContractsClient contractsClient;

    @Transactional
    public ReviewDto create(Long reviewerId, Long targetUserId, ReviewDto dto) {
        if (reviewerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (reviewerId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot review yourself.");
        }
        double rating = dto.rating();
        // Half-star granularity: rating must be a multiple of 0.5.
        if (Math.abs(rating * 2 - Math.round(rating * 2)) > 1e-9) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rating must be in steps of 0.5 (e.g. 4 or 4.5).");
        }
        String contractId = dto.contractId();
        if (contractId == null || contractId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A contract is required to leave a review.");
        }

        // Fail closed: only allow the review if ms-contracts confirms a delivered
        // contract between exactly these two users.
        ContractsClient.ContractSummary contract = contractsClient.getContract(contractId);
        if (contract == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not verify the transaction for this review. Please try again later.");
        }
        if (!contract.reviewable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can only review once the item has been delivered (or, for rentals, returned).");
        }
        String reviewer = String.valueOf(reviewerId);
        String target = String.valueOf(targetUserId);
        boolean validParties =
                (reviewer.equals(contract.ownerId()) && target.equals(contract.receiverId()))
                        || (reviewer.equals(contract.receiverId()) && target.equals(contract.ownerId()));
        if (!validParties) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only review the other party of your own transaction.");
        }
        if (reviewRepository.existsByReviewerIdAndContractId(reviewerId, contractId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have already reviewed this transaction.");
        }
        if (userRepository.findById(targetUserId).filter(u -> u.getDeletedAt() == null).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        Review review = new Review();
        review.setId(UUID.randomUUID().toString());
        review.setReviewerId(reviewerId);
        review.setTargetUserId(targetUserId);
        review.setContractId(contractId);
        review.setRating(rating);
        review.setComment(dto.comment());
        review.setCreatedAt(LocalDateTime.now());
        Review saved = reviewRepository.save(review);
        return toDto(saved, userRepository.findById(reviewerId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listForTarget(Long targetUserId) {
        List<Review> reviews = reviewRepository.findByTargetUserIdOrderByCreatedAtDesc(targetUserId);
        if (reviews.isEmpty()) return List.of();
        Map<Long, User> reviewers = userRepository.findAllById(
                        reviews.stream().map(Review::getReviewerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return reviews.stream()
                .map(r -> toDto(r, reviewers.get(r.getReviewerId())))
                .toList();
    }

    /** Average rating (null when no reviews) and count, for a user's public profile. */
    @Transactional(readOnly = true)
    public ReviewStats statsForTarget(Long targetUserId) {
        long count = reviewRepository.countByTargetUserId(targetUserId);
        Double avg = count == 0 ? null : reviewRepository.averageRating(targetUserId);
        return new ReviewStats(avg, count);
    }

    /** Batched stats keyed by target user id, for building many public profiles at once. */
    @Transactional(readOnly = true)
    public Map<Long, ReviewStats> statsForTargets(Collection<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) return Map.of();
        Map<Long, ReviewStats> out = new java.util.HashMap<>();
        for (Object[] row : reviewRepository.aggregateForTargets(targetUserIds)) {
            Long id = (Long) row[0];
            Double avg = row[1] == null ? null : ((Number) row[1]).doubleValue();
            long count = ((Number) row[2]).longValue();
            out.put(id, new ReviewStats(avg, count));
        }
        return out;
    }

    public record ReviewStats(Double average, long count) {}

    /** Removes every review written by or about a user (account deletion cleanup). */
    @Transactional
    public void deleteAllForUser(Long userId) {
        if (userId == null) return;
        reviewRepository.deleteByReviewerIdOrTargetUserId(userId, userId);
    }

    private ReviewDto toDto(Review r, User reviewer) {
        return new ReviewDto(r.getId(), r.getReviewerId(),
                reviewer != null ? reviewer.getPublicId() : null,
                displayName(reviewer), r.getTargetUserId(),
                r.getContractId(), r.getRating(), r.getComment(), r.getCreatedAt());
    }

    private String displayName(User user) {
        if (user == null) return "Unknown user";
        String name = List.of(
                        user.getFirstName() == null ? "" : user.getFirstName(),
                        user.getLastName() == null ? "" : user.getLastName())
                .stream().filter(s -> !s.isBlank()).map(String::trim).collect(Collectors.joining(" "));
        return name.isBlank() ? "User " + user.getId() : name;
    }
}
