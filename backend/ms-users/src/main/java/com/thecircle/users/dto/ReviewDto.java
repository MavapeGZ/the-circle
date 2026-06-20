package com.thecircle.users.dto;

import com.thecircle.users.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ReviewDto(
    // id, targetUserId and createdAt are assigned server-side and ignored on input.
    String id,

    @Size(max = 64, message = "Reviewer id must be at most 64 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Reviewer id " + ValidationPatterns.NO_ANGLE_MSG)
    String reviewerId,

    String targetUserId,

    @Size(max = 64, message = "Contract id must be at most 64 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Contract id " + ValidationPatterns.NO_ANGLE_MSG)
    String contractId,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    Integer rating,

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Comment " + ValidationPatterns.NO_ANGLE_MSG)
    String comment,

    LocalDateTime createdAt
) {}
