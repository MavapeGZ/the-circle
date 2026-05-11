package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.dto.VisualSignatureDto;
import com.thecircle.contracts.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Service
public class SignatureService {

    public byte[] applyVisualSignature(byte[] pdfBytes, VisualSignatureDto options) throws IOException {
        return applyVisualSignature(pdfBytes, options, Collections.emptyMap());
    }

    public byte[] applyVisualSignature(byte[] pdfBytes, VisualSignatureDto options, Map<String, SignerDto> signers) throws IOException {
        if (signers == null) signers = Collections.emptyMap();

        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            Map<String, VisualSignatureDto.SignaturePlacement> placements = options != null ? options.getPlacements() : null;
            if (placements == null || placements.isEmpty()) {
                return saveToBytes(doc);
            }

            int totalPages = doc.getNumberOfPages();
            for (Map.Entry<String, VisualSignatureDto.SignaturePlacement> entry : placements.entrySet()) {
                String key = entry.getKey();
                VisualSignatureDto.SignaturePlacement p = entry.getValue();
                if (p == null) continue;

                int pageIndex = Math.max(0, Math.min(p.getPage(), totalPages - 1));
                PDPage page = doc.getPage(pageIndex);
                SignerDto signer = signers.get(key);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                    renderPlacement(doc, cs, p, signer, key);
                }
            }

            return saveToBytes(doc);
        }
    }

    private void renderPlacement(PDDocument doc, PDPageContentStream cs,
                                 VisualSignatureDto.SignaturePlacement p, SignerDto signer, String key) throws IOException {
        String imageBase64 = signer != null ? signer.getSignatureImageBase64() : null;
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            PDImageXObject image = PdfUtils.imageFromBase64(doc, imageBase64);
            if (image != null) {
                cs.drawImage(image, p.getX(), p.getY(), p.getWidth(), p.getHeight());
                drawCaption(cs, p, signer);
                return;
            }
        }

        String text = resolveSignatureText(signer, key);
        float fontSize = computeFontSize(text, p.getWidth(), p.getHeight());
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_OBLIQUE, fontSize);
        cs.newLineAtOffset(p.getX(), p.getY() + p.getHeight() / 2f - fontSize / 2f);
        cs.showText(text);
        cs.endText();
        drawCaption(cs, p, signer);
    }

    private void drawCaption(PDPageContentStream cs, VisualSignatureDto.SignaturePlacement p, SignerDto signer) throws IOException {
        if (signer == null) return;
        String fullName = signer.getFullName();
        if (fullName == null || fullName.isEmpty()) return;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 8);
        cs.newLineAtOffset(p.getX(), p.getY() - 10f);
        cs.showText(fullName);
        cs.endText();
    }

    private String resolveSignatureText(SignerDto signer, String key) {
        if (signer != null) {
            if (signer.getSignatureText() != null && !signer.getSignatureText().isEmpty()) return signer.getSignatureText();
            if (signer.getFullName() != null && !signer.getFullName().isEmpty()) return signer.getFullName();
        }
        return key != null ? key : "Signed";
    }

    private float computeFontSize(String text, float boxWidth, float boxHeight) {
        float maxByHeight = Math.max(6f, boxHeight * 0.6f);
        if (text == null || text.isEmpty()) return Math.min(14f, maxByHeight);
        try {
            float widthAt1pt = PDType1Font.HELVETICA_OBLIQUE.getStringWidth(text) / 1000f;
            if (widthAt1pt <= 0) return Math.min(14f, maxByHeight);
            float maxByWidth = boxWidth / widthAt1pt;
            return Math.max(6f, Math.min(maxByHeight, maxByWidth));
        } catch (IOException | IllegalArgumentException ex) {
            return Math.min(14f, maxByHeight);
        }
    }

    private byte[] saveToBytes(PDDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
