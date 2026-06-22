package com.thecircle.users.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Defence-in-depth validation for uploaded files (avatars and KYC documents).
 *
 * <p>The {@code Content-Type} header and the filename extension are both
 * attacker-controlled, so neither is trusted on its own. For every upload we:
 * <ol>
 *   <li>reject empty / oversized files,</li>
 *   <li>require a single, well-formed extension from the allow-list (so
 *       {@code evil.php.jpg} or {@code shell.jsp} are rejected),</li>
 *   <li>sniff the leading "magic bytes" and require the real content type to be
 *       in the allow-list <em>and</em> to agree with both the declared
 *       {@code Content-Type} and the filename extension.</li>
 * </ol>
 * Any failure throws {@link IllegalArgumentException} with an English message
 * the controller surfaces as a 400.
 */
public final class UploadValidation {

    private UploadValidation() {
    }

    /** The file kinds we are willing to accept anywhere in the app. */
    public enum FileType {
        JPEG, PNG, PDF
    }

    /** Profile pictures: raster images only, JPEG or PNG. */
    public static final Set<FileType> IMAGE_TYPES = EnumSet.of(FileType.JPEG, FileType.PNG);

    /** KYC identity documents: images or a scanned PDF. */
    public static final Set<FileType> DOCUMENT_TYPES = EnumSet.of(FileType.JPEG, FileType.PNG, FileType.PDF);

    /**
     * Validates a single uploaded part. The {@code partName} is woven into the
     * error message (e.g. "front", "back", "image") so the caller knows which
     * file was rejected.
     */
    public static void validate(MultipartFile file, String partName, Set<FileType> allowed, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The " + partName + " file is required and cannot be empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "The " + partName + " file is too large. The maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
        }

        FileType byExtension = extensionType(file.getOriginalFilename());
        if (byExtension == null || !allowed.contains(byExtension)) {
            throw new IllegalArgumentException(
                    "The " + partName + " file name may only contain letters, numbers, spaces and the"
                            + " characters _ - ( ), and must end in a single " + describe(allowed)
                            + " extension (no extra or double extensions).");
        }

        FileType byContentType = contentTypeType(file.getContentType());
        if (byContentType == null || byContentType != byExtension) {
            throw new IllegalArgumentException(
                    "The declared type of the " + partName + " file does not match its extension.");
        }

        FileType byMagic = detectMagicBytes(file);
        if (byMagic == null || !allowed.contains(byMagic) || byMagic != byExtension) {
            throw new IllegalArgumentException(
                    "The " + partName + " file content does not match a real " + describe(allowed) + " file.");
        }
    }

    private static FileType extensionType(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.trim().toLowerCase(Locale.ROOT);
        // Base name = letters, numbers, spaces and _ - ( ), then a single dot and
        // an allowed extension. Real filenames like "my photo (1).png" or
        // "Captura 2024.png" pass; a dot in the base ("evil.php.jpg" double
        // extension), path separators ("..jpg" / "a/b.png"), and other symbols
        // are all rejected. Keep this charset in sync with the error message
        // built in validate().
        if (!name.matches("^[\\p{L}\\p{N} _()\\-]+\\.(jpg|jpeg|png|pdf)$")) {
            return null;
        }
        String ext = name.substring(name.lastIndexOf('.') + 1);
        return switch (ext) {
            case "jpg", "jpeg" -> FileType.JPEG;
            case "png" -> FileType.PNG;
            case "pdf" -> FileType.PDF;
            default -> null;
        };
    }

    private static FileType contentTypeType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase(Locale.ROOT).trim()) {
            case "image/jpeg", "image/jpg" -> FileType.JPEG;
            case "image/png" -> FileType.PNG;
            case "application/pdf" -> FileType.PDF;
            default -> null;
        };
    }

    private static FileType detectMagicBytes(MultipartFile file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return FileType.JPEG;
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A && (head[6] & 0xFF) == 0x1A
                && (head[7] & 0xFF) == 0x0A) {
            return FileType.PNG;
        }
        if (read >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-') {
            return FileType.PDF;
        }
        return null;
    }

    private static String describe(Set<FileType> allowed) {
        StringBuilder sb = new StringBuilder();
        if (allowed.contains(FileType.JPEG)) sb.append("JPG/");
        if (allowed.contains(FileType.PNG)) sb.append("PNG/");
        if (allowed.contains(FileType.PDF)) sb.append("PDF/");
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
