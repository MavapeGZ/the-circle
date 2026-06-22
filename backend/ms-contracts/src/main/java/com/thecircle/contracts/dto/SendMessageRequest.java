package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message body is required")
        @Size(max = 4000, message = "Message must be at most 4000 characters")
        @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Message " + ValidationPatterns.NO_ANGLE_MSG)
        String body) {
}
