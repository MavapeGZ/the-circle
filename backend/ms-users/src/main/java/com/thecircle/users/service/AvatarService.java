package com.thecircle.users.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Stores and serves user profile pictures on local disk, one folder per user.
 * Mirrors the path-traversal hardening used by {@link KycService}: only a single
 * file is kept per user, and the on-disk name is generated server-side so a
 * crafted upload filename can never escape the user's directory.
 */
@Service
@Slf4j
public class AvatarService {

    // Keep avatars small; these are display thumbnails, not document scans.
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path uploadsRoot;

    public AvatarService(
            @Value("${avatar.upload-dir:${user.home}/the-circle/uploads/avatars}") String uploadsRoot) {
        this.uploadsRoot = Path.of(uploadsRoot).toAbsolutePath().normalize();
    }

    /**
     * Validates and stores the uploaded image, replacing any previous avatar for
     * the user. Returns the generated filename to persist on the user row.
     */
    public String store(Long userId, MultipartFile file) throws IOException {
        // Non-empty, size-bounded, single safe extension, declared type and real
        // magic bytes all agreeing on JPG or PNG. Rejects disguised uploads
        // (e.g. an HTML/SVG/script file renamed to .png).
        UploadValidation.validate(file, "image", UploadValidation.IMAGE_TYPES, MAX_BYTES);

        Path userDir = uploadsRoot.resolve(String.valueOf(userId)).normalize();
        if (!userDir.startsWith(uploadsRoot)) {
            throw new IOException("Resolved avatar path escaped the uploads root.");
        }
        Files.createDirectories(userDir);

        // Drop any prior avatar so a user never accumulates files on disk.
        deleteExisting(userDir);

        String filename = "avatar-" + System.currentTimeMillis() + extensionFor(file.getContentType());
        Path target = userDir.resolve(filename).normalize();
        if (!target.startsWith(userDir)) {
            throw new IOException("Resolved avatar file escaped the user directory.");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return filename;
    }

    /** Reads the stored avatar bytes for a user, or null when the file is gone. */
    public byte[] load(Long userId, String filename) throws IOException {
        if (filename == null) return null;
        Path file = resolve(userId, filename);
        if (file == null || !Files.exists(file)) return null;
        return Files.readAllBytes(file);
    }

    /** Removes the user's stored avatar files, ignoring a missing folder. */
    public void delete(Long userId) {
        Path userDir = uploadsRoot.resolve(String.valueOf(userId)).normalize();
        if (userDir.startsWith(uploadsRoot)) {
            deleteExisting(userDir);
        }
    }

    public MediaType mediaTypeFor(String filename) {
        // Avatars are validated to JPEG or PNG only (see UploadValidation.IMAGE_TYPES).
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.IMAGE_JPEG;
    }

    private Path resolve(Long userId, String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        Path userDir = uploadsRoot.resolve(String.valueOf(userId)).normalize();
        Path resolved = userDir.resolve(filename).normalize();
        return resolved.startsWith(userDir) ? resolved : null;
    }

    private void deleteExisting(Path userDir) {
        if (!Files.isDirectory(userDir)) return;
        try (var stream = Files.list(userDir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("avatar-"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete old avatar {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to list avatar dir {}", userDir, e);
        }
    }

    private String extensionFor(String contentType) {
        // Only JPEG/PNG reach here; PNG keeps its extension, everything else is JPEG.
        return "image/png".equalsIgnoreCase(contentType) ? ".png" : ".jpg";
    }
}
