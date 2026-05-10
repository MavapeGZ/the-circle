package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class MockKycProvider implements KycValidationService {

    private static final long MAX_BYTES = 5 * 1024 * 1024L; // 5 MB

    @Override
    public boolean validate(Long userId, MultipartFile front, MultipartFile back) {
        if (front == null || front.isEmpty() || back == null || back.isEmpty()) {
            log.debug("MockKycProvider: missing front/back files for user {}", userId);
            return false;
        }

        if (front.getSize() > MAX_BYTES || back.getSize() > MAX_BYTES) {
            log.debug("MockKycProvider: file too large for user {}", userId);
            return false;
        }

        // Simple filename check: ensure file names have allowed extensions
        String fName = front.getOriginalFilename();
        String bName = back.getOriginalFilename();
        if (fName == null || bName == null) return false;
        String lf = fName.toLowerCase();
        String lb = bName.toLowerCase();
        boolean okExt = (lf.endsWith(".png") || lf.endsWith(".jpg") || lf.endsWith(".jpeg") || lf.endsWith(".pdf"))
                && (lb.endsWith(".png") || lb.endsWith(".jpg") || lb.endsWith(".jpeg") || lb.endsWith(".pdf"));
        if (!okExt) return false;

        // For the mock, we accept the document
        log.debug("MockKycProvider: validation passed for user {}", userId);
        return true;
    }
}
