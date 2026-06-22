package com.thecircle.users.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadValidationTest {

    private static final long MAX = 5L * 1024 * 1024;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile f = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "front", UploadValidation.DOCUMENT_TYPES, MAX));
    }

    @Test
    void rejectsOversizedFile() {
        MockMultipartFile f = new MockMultipartFile("front", "front.jpg", "image/jpeg", JPEG);
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "front", UploadValidation.DOCUMENT_TYPES, 2L));
    }

    @Test
    void rejectsDoubleExtension() {
        MockMultipartFile f = new MockMultipartFile("front", "evil.php.jpg", "image/jpeg", JPEG);
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "front", UploadValidation.DOCUMENT_TYPES, MAX));
    }

    @Test
    void rejectsDisallowedExtension() {
        MockMultipartFile f = new MockMultipartFile("front", "shell.svg", "image/svg+xml", JPEG);
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "front", UploadValidation.DOCUMENT_TYPES, MAX));
    }

    @Test
    void rejectsContentTypeMismatch() {
        MockMultipartFile f = new MockMultipartFile("front", "front.png", "image/jpeg", PNG);
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "front", UploadValidation.DOCUMENT_TYPES, MAX));
    }

    @Test
    void rejectsMagicByteMismatch() {
        // declared + extension say PNG, but the bytes are not a real PNG
        MockMultipartFile f = new MockMultipartFile("front", "front.png", "image/png", new byte[]{1, 2, 3, 4, 5});
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "front", UploadValidation.DOCUMENT_TYPES, MAX));
    }

    @Test
    void rejectsPdfForImageOnlyTarget() {
        MockMultipartFile f = new MockMultipartFile("image", "doc.pdf", "application/pdf", PDF);
        assertThrows(IllegalArgumentException.class,
                () -> UploadValidation.validate(f, "image", UploadValidation.IMAGE_TYPES, MAX));
    }

    @Test
    void acceptsFilenamesWithSpacesAndParentheses() {
        // Real-world uploads ("Captura 2024.png", "my photo (1).png") carry
        // spaces and parentheses; only the extension and bytes must check out.
        MockMultipartFile png = new MockMultipartFile("file", "my photo (1).png", "image/png", PNG);
        MockMultipartFile jpg = new MockMultipartFile("file", "Captura de pantalla 2024.jpg", "image/jpeg", JPEG);
        assertDoesNotThrow(() -> UploadValidation.validate(png, "image", UploadValidation.IMAGE_TYPES, MAX));
        assertDoesNotThrow(() -> UploadValidation.validate(jpg, "image", UploadValidation.IMAGE_TYPES, MAX));
    }

    @Test
    void acceptsValidJpegPngAndPdf() {
        MockMultipartFile jpg = new MockMultipartFile("front", "front.jpg", "image/jpeg", JPEG);
        MockMultipartFile png = new MockMultipartFile("back", "back.png", "image/png", PNG);
        MockMultipartFile pdf = new MockMultipartFile("front", "scan.pdf", "application/pdf", PDF);
        assertDoesNotThrow(() -> UploadValidation.validate(jpg, "front", UploadValidation.DOCUMENT_TYPES, MAX));
        assertDoesNotThrow(() -> UploadValidation.validate(png, "back", UploadValidation.DOCUMENT_TYPES, MAX));
        assertDoesNotThrow(() -> UploadValidation.validate(pdf, "front", UploadValidation.DOCUMENT_TYPES, MAX));
        // image-only target accepts the JPEG too
        assertDoesNotThrow(() -> UploadValidation.validate(jpg, "image", UploadValidation.IMAGE_TYPES, MAX));
    }
}
