package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.dto.SignerDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class ContractPdfServiceTest {

    private final ContractPdfService service = new ContractPdfService(templateEngine());

    /** Stand-alone Thymeleaf engine wired to the i18n bundles, mirroring the Spring autoconfig. */
    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("i18n/messages");
        ms.setDefaultEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(ms);
        return engine;
    }

    private static ContractDto sampleContract() {
        ContractDto dto = new ContractDto();
        dto.setContractId("c1");
        dto.setType(ContractType.SALE);
        dto.setPrice(new BigDecimal("120.00"));
        dto.setStartDate(LocalDate.of(2025, 1, 1));
        dto.setEndDate(LocalDate.of(2026, 1, 1));
        SignerDto s1 = new SignerDto(); s1.setFullName("Juan Perez"); s1.setIdNumber("12345678Z");
        SignerDto s2 = new SignerDto(); s2.setFullName("Maria Lopez"); s2.setIdNumber("87654321X");
        dto.setPrimarySigner(s1);
        dto.setSecondarySigner(s2);
        return dto;
    }

    @Test
    public void generatePdf_shouldContainPartyNamesAndIds() throws Exception {
        byte[] pdfBytes = service.generatePdf(sampleContract(), "en");
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        String text = extractText(pdfBytes);
        assertTrue(text.contains("Juan Perez"));
        assertTrue(text.contains("Maria Lopez"));
        assertTrue(text.contains("12345678Z"));
        // The legal scaffolding (eIDAS clause) must be present.
        assertTrue(text.contains("eIDAS"));
    }

    @Test
    public void generatePdf_bothSigned_showsBothTimestamps() throws Exception {
        ContractDto dto = sampleContract();
        dto.setReceiverSignedAt(LocalDateTime.of(2025, 6, 1, 10, 30));
        dto.setOwnerSignedAt(LocalDateTime.of(2025, 6, 2, 11, 45));

        String text = extractText(service.generatePdf(dto, "es"));
        assertTrue(text.contains("01/06/2025 10:30"));
        assertTrue(text.contains("02/06/2025 11:45"));
    }

    @Test
    public void generatePdf_unsigned_marksSignaturesPending() throws Exception {
        String text = extractText(service.generatePdf(sampleContract(), "es"));
        assertTrue(text.contains("Pendiente de firma"));
    }

    private static String extractText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            assertTrue(doc.getNumberOfPages() >= 1);
            return new PDFTextStripper().getText(doc);
        }
    }
}
