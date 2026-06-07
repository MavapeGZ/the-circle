package com.thecircle.contracts.service;

import com.thecircle.contracts.model.SignatureRecord;
import com.thecircle.contracts.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SignatureAuditService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");

    public byte[] appendAuditPage(byte[] pdfBytes, List<SignatureRecord> records, String currentDocumentHash) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writeAuditContent(cs, page.getMediaBox(), records, currentDocumentHash);
            }
            return PdfUtils.toByteArray(doc);
        }
    }

    private void writeAuditContent(PDPageContentStream cs, PDRectangle box, List<SignatureRecord> records, String currentDocumentHash) throws IOException {
        float marginX = 50f;
        float y = box.getHeight() - 60f;

        PdfUtils.drawTextCentered(cs, box, "Advanced electronic signature record", y);
        y -= 18f;
        drawLine(cs, marginX, y, "Regulation (EU) 910/2014 - eIDAS", PDType1Font.HELVETICA_OBLIQUE, 9);
        y -= 24f;

        drawLine(cs, marginX, y, "Document signed using unique OTP codes sent to the signer.", PDType1Font.HELVETICA, 10);
        y -= 14f;
        drawLine(cs, marginX, y, "Integrity guaranteed with SHA-256 cryptographic hash.", PDType1Font.HELVETICA, 10);
        y -= 24f;

        if (records == null || records.isEmpty()) {
            drawLine(cs, marginX, y, "No signatures recorded.", PDType1Font.HELVETICA_OBLIQUE, 10);
            return;
        }

        int idx = 1;
        for (SignatureRecord r : records) {
            drawLine(cs, marginX, y, "Signature " + idx + " - " + safe(r.getSignerKey()), PDType1Font.HELVETICA_BOLD, 11);
            y -= 14f;
            y = drawField(cs, marginX, y, "Name", safe(r.getSignerFullName()));
            y = drawField(cs, marginX, y, "ID number", safe(r.getSignerIdNumber()));
            y = drawField(cs, marginX, y, "Email", safe(r.getSignerEmail()));
            y = drawField(cs, marginX, y, "Date (UTC)", r.getSignedAtUtc() != null ? r.getSignedAtUtc().format(TS) : "-");
            y = drawField(cs, marginX, y, "IP", safe(r.getIp()));
            y = drawField(cs, marginX, y, "User-Agent", truncate(safe(r.getUserAgent()), 80));
            y = drawField(cs, marginX, y, "OTP session ID", safe(r.getOtpSessionId()));
            y = drawField(cs, marginX, y, "Algorithm", safe(r.getAlgorithm()));
            y = drawField(cs, marginX, y, "Previous hash", truncate(safe(r.getPreHash()), 64));
            y = drawField(cs, marginX, y, "Signed hash", truncate(safe(r.getPostHash()), 64));
            y -= 10f;
            idx++;
        }

        y -= 6f;
        drawLine(cs, marginX, y, "Current document hash: " + safe(currentDocumentHash), PDType1Font.HELVETICA_OBLIQUE, 8);
    }

    private float drawField(PDPageContentStream cs, float x, float y, String label, String value) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
        cs.newLineAtOffset(x, y);
        cs.showText(label + ":");
        cs.endText();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 9);
        cs.newLineAtOffset(x + 90f, y);
        cs.showText(value == null ? "-" : value);
        cs.endText();
        return y - 12f;
    }

    private void drawLine(PDPageContentStream cs, float x, float y, String text, PDType1Font font, float size) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private String safe(String s) { return s == null ? "-" : s; }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
