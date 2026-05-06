package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ContractPdfService {

    public byte[] generatePdf(ContractDto dto) throws IOException {
        PDDocument doc = new PDDocument();
        try {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            PDRectangle rect = page.getMediaBox();

            // Title
            PdfUtils.drawTextCentered(cs, rect, "CONTRATO DE ARRENDAMIENTO CONJUNTO", rect.getHeight() - 50);

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 11);
            cs.newLineAtOffset(50, rect.getHeight() - 100);

            cs.showText("Entre: " + (dto.getPrimarySigner() != null ? dto.getPrimarySigner().getFullName() : "[N/A]") );
            cs.newLineAtOffset(0, -15);
            cs.showText("Y: " + (dto.getSecondarySigner() != null ? dto.getSecondarySigner().getFullName() : "[N/A]") );
            cs.newLineAtOffset(0, -15);
            cs.showText("Domicilio del inmueble: " + (dto.getPropertyAddress() != null ? dto.getPropertyAddress() : "[N/A]") );
            cs.newLineAtOffset(0, -15);
            cs.showText("Periodo: " + (dto.getStartDate() != null ? dto.getStartDate().toString() : "[N/A]") + " - " + (dto.getEndDate() != null ? dto.getEndDate().toString() : "[N/A]") );
            cs.newLineAtOffset(0, -15);
            cs.showText("Renta mensual: " + (dto.getMonthlyRent() != null ? dto.getMonthlyRent().toString() : "[N/A]") );

            cs.endText();

            cs.close();

            return PdfUtils.toByteArray(doc);
        } finally {
            doc.close();
        }
    }
}

