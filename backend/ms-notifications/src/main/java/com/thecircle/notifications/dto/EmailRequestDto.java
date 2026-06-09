package com.thecircle.notifications.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequestDto {

    @NotBlank
    @Email
    @Size(max = 320)
    private String to;

    @NotBlank
    @Size(max = 255)
    private String subject;

    @NotBlank
    @Size(max = 100)
    private String templateName;

    private Map<String, Object> variables;
}
