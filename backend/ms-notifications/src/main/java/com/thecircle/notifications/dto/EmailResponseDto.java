package com.thecircle.notifications.dto;

import com.thecircle.notifications.model.EmailStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponseDto {
    private Long id;
    private EmailStatus status;
    private Instant sentAt;
}
