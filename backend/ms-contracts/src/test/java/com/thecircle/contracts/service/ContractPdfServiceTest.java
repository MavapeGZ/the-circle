package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.SignerDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class ContractPdfServiceTest {

    private final ContractPdfService service = new ContractPdfService(messageSource());

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/messages");
        ms.setDefaultEncoding("UTF-8");
        return ms;
    }

    @Test
    public void generatePdf_shouldContainNames() throws Exception {
        ContractDto dto = new ContractDto();
        dto.setContractId("c1");
        dto.setPropertyAddress("Calle Falsa 123");
        dto.setStartDate(LocalDate.of(2025,1,1));
        dto.setEndDate(LocalDate.of(2026,1,1));
        dto.setMonthlyRent(new BigDecimal("750.00"));
        SignerDto s1 = new SignerDto(); s1.setFullName("Juan Perez"); s1.setIdNumber("1234");
        SignerDto s2 = new SignerDto(); s2.setFullName("Maria Lopez"); s2.setIdNumber("5678");
        dto.setPrimarySigner(s1); dto.setSecondarySigner(s2);

        byte[] pdfBytes = service.generatePdf(dto, "en");
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            assertTrue(text.contains("Juan Perez"));
            assertTrue(text.contains("Maria Lopez"));
            assertTrue(doc.getNumberOfPages() >= 1);
        }
    }
}

