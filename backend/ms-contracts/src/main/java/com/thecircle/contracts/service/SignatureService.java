package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.VisualSignatureDto;
import com.thecircle.contracts.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class SignatureService {

    public byte[] applyVisualSignature(byte[] pdfBytes, VisualSignatureDto options) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            Map<String, VisualSignatureDto.SignaturePlacement> placements = options != null ? options.getPlacements() : null;

            if (placements != null) {
                for (Map.Entry<String, VisualSignatureDto.SignaturePlacement> entry : placements.entrySet()) {
                    String key = entry.getKey();
                    VisualSignatureDto.SignaturePlacement p = entry.getValue();

                    int pageIndex = Math.max(0, Math.min(p.getPage(), doc.getNumberOfPages() - 1));
                    PDPage page = doc.getPage(pageIndex);
                    PDRectangle rect = page.getMediaBox();

                    PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true);

                    // For simplicity, draw placeholder text with signer key
                    cs.beginText();
                    cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_OBLIQUE, 10);
                    cs.newLineAtOffset(p.getX(), p.getY());
                    cs.showText("Firma: " + key);
                    cs.endText();

                    // If image present for this signer, draw it
                    // NOTE: caller should have embedded image into DTO or provided mapping; here we do not have direct access to images

                    cs.close();
                }
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                doc.save(baos);
                return baos.toByteArray();
            }
        }
    }
}

