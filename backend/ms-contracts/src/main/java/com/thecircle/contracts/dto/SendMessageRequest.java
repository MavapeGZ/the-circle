package com.thecircle.contracts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A chat message body. No angle-bracket filter here (unlike titles/conditions):
 * chat is free text where "<", ">" and "→" are legitimate, and the frontend
 * renders it as text (React-escaped), so stored-XSS is not a concern.
 */
public record SendMessageRequest(
        @NotBlank(message = "Message body is required")
        @Size(max = 4000, message = "Message must be at most 4000 characters")
        String body) {
}
