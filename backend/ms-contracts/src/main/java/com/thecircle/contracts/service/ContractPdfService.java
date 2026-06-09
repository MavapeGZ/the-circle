package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;

@Service
public class ContractPdfService {

    private static final String NA = "[N/A]";

    public byte[] generatePdf(ContractDto dto) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDRectangle rect = page.getMediaBox();
                float width = rect.getWidth();
                float height = rect.getHeight();

                drawTitle(cs, width, height - 80f, "The Circle");
                drawSubtitle(cs, width, height - 110f, "Contract");

                SignerDto p1 = dto.getPrimarySigner();
                SignerDto p2 = dto.getSecondarySigner();

                String name1 = p1 != null && p1.getFullName() != null ? p1.getFullName() : NA;
                String name2 = p2 != null && p2.getFullName() != null ? p2.getFullName() : NA;
                String addr1 = p1 != null && p1.getAddress() != null ? p1.getAddress() : NA;
                String addr2 = p2 != null && p2.getAddress() != null ? p2.getAddress() : NA;
                String id1 = p1 != null && p1.getIdNumber() != null ? p1.getIdNumber() : NA;
                String id2 = p2 != null && p2.getIdNumber() != null ? p2.getIdNumber() : NA;
                String type = typeLabel(dto.getType());
                String price = formatPrice(dto.getPrice());

                float x = 60f;
                float y = height - 170f;
                float lineGap = 18f;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(x, y);
                cs.showText("Party A");
                cs.endText();
                y -= lineGap;

                y = drawLabelValue(cs, x, y, lineGap, "Full name:", name1);
                y = drawLabelValue(cs, x, y, lineGap, "ID number:", id1);
                y = drawLabelValue(cs, x, y, lineGap, "Address:", addr1);

                y -= lineGap;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(x, y);
                cs.showText("Party B");
                cs.endText();
                y -= lineGap;

                y = drawLabelValue(cs, x, y, lineGap, "Full name:", name2);
                y = drawLabelValue(cs, x, y, lineGap, "ID number:", id2);
                y = drawLabelValue(cs, x, y, lineGap, "Address:", addr2);

                y -= lineGap;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(x, y);
                cs.showText("Transaction");
                cs.endText();
                y -= lineGap;

                y = drawLabelValue(cs, x, y, lineGap, "Type:", type);
                y = drawLabelValue(cs, x, y, lineGap, "Price:", price);
            }

            return PdfUtils.toByteArray(doc);
        }
    }

    private void drawTitle(PDPageContentStream cs, float pageWidth, float y, String text) throws IOException {
        float fontSize = 24f;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
        float textWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(text) / 1000f * fontSize;
        cs.newLineAtOffset((pageWidth - textWidth) / 2f, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawSubtitle(PDPageContentStream cs, float pageWidth, float y, String text) throws IOException {
        float fontSize = 14f;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, fontSize);
        float textWidth = PDType1Font.HELVETICA.getStringWidth(text) / 1000f * fontSize;
        cs.newLineAtOffset((pageWidth - textWidth) / 2f, y);
        cs.showText(text);
        cs.endText();
    }

    private float drawLabelValue(PDPageContentStream cs, float x, float y, float lineGap, String label, String value) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
        cs.newLineAtOffset(x + 10f, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 11);
        cs.newLineAtOffset(x + 110f, y);
        cs.showText(value);
        cs.endText();

        return y - lineGap;
    }

    /** User-facing label for the contract type (mirrors the frontend mapping). */
    private String typeLabel(com.thecircle.contracts.dto.ContractType type) {
        if (type == null) return NA;
        return switch (type) {
            case SALE -> "Sale";
            case RENT -> "Rental";
            case CESSION_TEMPORARY -> "Loan";
            case CESSION_PERMANENT -> "Donation";
        };
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return NA;
        return price.toPlainString() + " EUR";
    }
}
