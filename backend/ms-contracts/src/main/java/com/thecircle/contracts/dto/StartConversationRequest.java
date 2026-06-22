package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartConversationRequest(
        @NotBlank(message = "articleId is required")
        @Size(max = 64, message = "articleId must be at most 64 characters")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "articleId " + ValidationPatterns.NO_ANGLE_MSG)
        String articleId) {
}
