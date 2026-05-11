package com.thecircle.contracts.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class PdfUtils {

    public static byte[] toByteArray(PDDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    public static PDImageXObject imageFromBase64(PDDocument doc, String base64) throws IOException {
        if (base64 == null || base64.isEmpty()) return null;
        final byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid base64 signature image", ex);
        }
        return PDImageXObject.createFromByteArray(doc, imageBytes, "signature");
    }

    public static void drawTextCentered(PDPageContentStream cs, PDRectangle pageSize, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
        float titleWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(text) / 1000 * 12;
        cs.newLineAtOffset((pageSize.getWidth() - titleWidth) / 2, y);
        cs.showText(text);
        cs.endText();
    }
}

