package com.thecircle.notifications.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

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

    // Either a literal subject OR a subjectKey (resolved from the i18n bundle
    // using `locale`) must be supplied. subjectKey takes precedence when present.
    @Size(max = 255)
    private String subject;

    @Size(max = 100)
    private String subjectKey;

    @NotBlank
    @Size(max = 100)
    private String templateName;

    // BCP-47 / ISO 639-1 language tag of the recipient (e.g. "es", "en").
    // Drives both subject resolution and the Thymeleaf template locale.
    // Null/blank falls back to the bundle default (English).
    @Size(max = 16)
    private String locale;

    private Map<String, Object> variables;

    @AssertTrue(message = "Either subject or subjectKey is required")
    public boolean isSubjectPresent() {
        return StringUtils.hasText(subject) || StringUtils.hasText(subjectKey);
    }
}
