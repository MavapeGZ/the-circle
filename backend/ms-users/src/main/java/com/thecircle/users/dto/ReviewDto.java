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

        // Opaque id of the reviewer so the UI can link to their profile without
        // exposing the sequential primary key. Server-assigned.
        String reviewerPublicId,

        String reviewerName,

        Long targetUserId,

        @Size(max = 64, message = "validation.contractId.size")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.contractId.noAngle")
        String contractId,

        @NotNull(message = "validation.rating.required")
        @DecimalMin(value = "1.0", message = "validation.rating.range")
        @DecimalMax(value = "5.0", message = "validation.rating.range")
        Double rating,

        @Size(max = 1000, message = "validation.comment.size")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.comment.noAngle")
        String comment,

        LocalDateTime createdAt
) {}
