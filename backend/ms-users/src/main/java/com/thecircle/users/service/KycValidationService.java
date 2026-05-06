package com.thecircle.users.service;

import org.springframework.web.multipart.MultipartFile;

public interface KycValidationService {
    /**
     * Validates the provided documents and returns true if the user should be VERIFIED.
     */
    boolean validate(Long userId, MultipartFile front, MultipartFile back) throws Exception;
}

