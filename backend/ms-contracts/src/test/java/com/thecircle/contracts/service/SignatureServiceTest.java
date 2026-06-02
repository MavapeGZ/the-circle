package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.dto.VisualSignatureDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SignatureServiceTest {

    private final SignatureService service = new SignatureService();

    @Test
    public void applyVisualSignature_withTextFallback_drawsSignerName() throws Exception {
        byte[] basePdf = blankPdf(1);

        VisualSignatureDto opts = new VisualSignatureDto();
        Map<String, VisualSignatureDto.SignaturePlacement> placements = new HashMap<>();
        placements.put("primarySigner", placement(0, 60f, 60f, 200f, 40f));
        opts.setPlacements(placements);

        SignerDto signer = new SignerDto();
        signer.setFullName("Jane Doe");
        Map<String, SignerDto> signers = new HashMap<>();
        signers.put("primarySigner", signer);

        byte[] signed = service.applyVisualSignature(basePdf, opts, signers);
        assertNotNull(signed);
        assertTrue(signed.length > basePdf.length);

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(signed))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            assertTrue(text.contains("Jane Doe"), "Expected signer name in output, got: " + text);
        }
    }

    @Test
    public void applyVisualSignature_clampsOutOfRangePageIndex() throws Exception {
        byte[] basePdf = blankPdf(2);

        VisualSignatureDto opts = new VisualSignatureDto();
        Map<String, VisualSignatureDto.SignaturePlacement> placements = new HashMap<>();
        placements.put("primarySigner", placement(99, 50f, 50f, 100f, 30f));
        opts.setPlacements(placements);

        byte[] signed = service.applyVisualSignature(basePdf, opts, Map.of());
        assertNotNull(signed);
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(signed))) {
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    public void applyVisualSignature_withImage_doesNotThrow() throws Exception {
        byte[] basePdf = blankPdf(1);

        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 40, 20);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        SignerDto signer = new SignerDto();
        signer.setFullName("Image Signer");
        signer.setSignatureImageBase64(base64);

        VisualSignatureDto opts = new VisualSignatureDto();
        Map<String, VisualSignatureDto.SignaturePlacement> placements = new HashMap<>();
        placements.put("primarySigner", placement(0, 80f, 80f, 100f, 40f));
        opts.setPlacements(placements);

        byte[] signed = service.applyVisualSignature(basePdf, opts, Map.of("primarySigner", signer));
        assertNotNull(signed);
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(signed))) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    public void applyVisualSignature_noPlacements_returnsSamePageCount() throws Exception {
        byte[] basePdf = blankPdf(1);
        byte[] signed = service.applyVisualSignature(basePdf, new VisualSignatureDto(), Map.of());
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(signed))) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    private VisualSignatureDto.SignaturePlacement placement(int page, float x, float y, float w, float h) {
        VisualSignatureDto.SignaturePlacement p = new VisualSignatureDto.SignaturePlacement();
        p.setPage(page);
        p.setX(x);
        p.setY(y);
        p.setWidth(w);
        p.setHeight(h);
        return p;
    }

    private byte[] blankPdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.LETTER));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
