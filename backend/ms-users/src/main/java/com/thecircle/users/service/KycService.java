package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.thecircle.users.model.User;
import com.thecircle.users.model.KycStatus;
import com.thecircle.users.repository.UserRepository;
import com.thecircle.users.security.JwtService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class KycService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final UserRepository userRepository;
    private final KycValidationService kycValidationService;
    private final JwtService jwtService;
    private final Path uploadsRoot;

    public KycService(
            UserRepository userRepository,
            KycValidationService kycValidationService,
            JwtService jwtService,
            @Value("${kyc.upload-dir:${user.home}/the-circle/uploads/users}") String uploadsRoot
    ) {
        this.userRepository = userRepository;
        this.kycValidationService = kycValidationService;
        this.jwtService = jwtService;
        this.uploadsRoot = Path.of(uploadsRoot).toAbsolutePath().normalize();
    }

    /**
     * Processes KYC: saves files, sets status to PENDING_REVIEW, calls provider.
     * Returns a refreshed JWT when user is VERIFIED, otherwise returns null.
     */
    public String processKyc(Long userId, MultipartFile front, MultipartFile back) throws Exception {
        Optional<User> maybe = userRepository.findById(userId);
        if (maybe.isEmpty()) return null;

        User user = maybe.get();
        KycStatus originalStatus = user.getKycStatus();

        // Ensure folder exists
        Path userDir = uploadsRoot.resolve(String.valueOf(userId));
        Files.createDirectories(userDir);

        // Save files
        String frontName = safeFilename(front.getOriginalFilename(), "front");
        String backName = safeFilename(back.getOriginalFilename(), "back");
        Path frontPath = resolveUserFile(userDir, frontName);
        Path backPath = resolveUserFile(userDir, backName);

        try {
            copyMultipartFile(front, frontPath);
            copyMultipartFile(back, backPath);
        } catch (IOException e) {
            deleteUploadedFiles(frontPath, backPath);
            log.error("Error saving uploaded files for user {}", userId, e);
            throw e;
        }

        user.setKycStatus(KycStatus.PENDING_REVIEW);
        userRepository.save(user);

        try {
            boolean ok = kycValidationService.validate(userId, front, back);

            if (ok) {
                user.setKycStatus(KycStatus.VERIFIED);
                userRepository.save(user);

                // regenerate jwt with claim
                Map<String, Object> claims = new HashMap<>();
                claims.put("kyc_verified", true);
                String token = jwtService.generateToken(claims, user);
                return token;
            }

            deleteUploadedFiles(frontPath, backPath);
            user.setKycStatus(KycStatus.REJECTED);
            userRepository.save(user);
            return null;
        } catch (Exception e) {
            deleteUploadedFiles(frontPath, backPath);
            user.setKycStatus(originalStatus);
            userRepository.save(user);
            throw e;
        }
    }

    private void copyMultipartFile(MultipartFile file, Path target) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteUploadedFiles(Path... paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Failed to delete uploaded file {}", path, e);
            }
        }
    }

    private Path resolveUserFile(Path userDir, String filename) throws IOException {
        if (filename.contains("/") || filename.contains("\\")) {
            throw new IOException("Invalid upload filename");
        }
        Path resolved = userDir.resolve(filename).normalize();
        if (!resolved.startsWith(userDir)) {
            throw new IOException("Invalid upload filename");
        }
        return resolved;
    }

    private String safeFilename(String original, String fallback) {
        String clean = sanitizeFilename(original);
        if (isUsableFilename(clean)) {
            return clean;
        }

        String safeFallback = sanitizeFilename(fallback);
        if (isUsableFilename(safeFallback)) {
            return safeFallback;
        }

        return "file";
    }

    private String sanitizeFilename(String filename) {
        if (filename != null) {
            if (filename.contains("/") || filename.contains("\\")) {
                return null;
            }
            String clean = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (clean.length() > MAX_FILENAME_LENGTH) {
                return truncateFilename(clean);
            }
            if (isUsableFilename(clean)) {
                return clean;
            }
        }
        return null;
    }

    private String truncateFilename(String filename) {
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == filename.length() - 1) {
            return filename.substring(0, MAX_FILENAME_LENGTH);
        }

        String extension = filename.substring(extensionSeparator);
        int maxBaseLength = MAX_FILENAME_LENGTH - extension.length();
        if (maxBaseLength <= 0) {
            return filename.substring(0, MAX_FILENAME_LENGTH);
        }

        String baseName = filename.substring(0, extensionSeparator);
        if (baseName.length() > maxBaseLength) {
            baseName = baseName.substring(0, maxBaseLength);
        }

        return baseName + extension;
    }

    private boolean isUsableFilename(String name) {
        return name != null && !name.trim().isEmpty() && !".".equals(name) && !"..".equals(name);
    }
}
