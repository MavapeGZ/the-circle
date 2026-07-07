package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartConversationRequest(
        @NotBlank(message = "validation.articleId.required")
        @Size(max = 64, message = "validation.articleId.size")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.articleId.noAngle")
        String articleId) {
}
