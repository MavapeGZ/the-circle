package com.thecircle.users.dto;

import com.thecircle.users.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * A review of one user by another. On input only {@code contractId}, {@code rating}
 * and {@code comment} are honoured: {@code id}, {@code reviewerId},
 * {@code reviewerName}, {@code targetUserId} and {@code createdAt} are assigned
 * server-side. The rating is a half-star value (1.0–5.0, in 0.5 steps; the step is
 * enforced in the service).
 */
public record ReviewDto(
        String id,

        Long reviewerId,

        String reviewerName,

        Long targetUserId,

        @Size(max = 64, message = "Contract id must be at most 64 characters")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Contract id " + ValidationPatterns.NO_ANGLE_MSG)
        String contractId,

        @NotNull(message = "Rating is required")
        @DecimalMin(value = "1.0", message = "Rating must be between 1 and 5")
        @DecimalMax(value = "5.0", message = "Rating must be between 1 and 5")
        Double rating,

        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Comment " + ValidationPatterns.NO_ANGLE_MSG)
        String comment,

        LocalDateTime createdAt
) {}
