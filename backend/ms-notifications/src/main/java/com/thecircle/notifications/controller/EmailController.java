package com.thecircle.notifications.controller;

import com.thecircle.notifications.dto.EmailRequestDto;
import com.thecircle.notifications.dto.EmailResponseDto;
import com.thecircle.notifications.service.EmailDeliveryException;
import com.thecircle.notifications.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class EmailController {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final EmailService emailService;

    @Value("${notifications.internal.api-key:}")
    private String internalApiKey;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ms-notifications OK");
    }

    @PostMapping("/email")
    public ResponseEntity<EmailResponseDto> sendEmail(@RequestBody @Valid EmailRequestDto dto,
            HttpServletRequest request) {
        assertInternalCaller(request);
        try {
            return ResponseEntity.ok(emailService.send(dto));
        } catch (EmailDeliveryException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to deliver email", e);
        }
    }

    private void assertInternalCaller(HttpServletRequest request) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            return;
        }
        if (!internalApiKey.equals(request.getHeader(INTERNAL_KEY_HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing internal API key");
        }
    }
}
