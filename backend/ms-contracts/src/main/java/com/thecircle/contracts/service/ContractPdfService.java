package com.thecircle.contracts.service;

import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.SignerDto;
import com.thecircle.contracts.util.PdfUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;

@Service
public class ContractPdfService {

    private final MessageSource messageSource;

    public ContractPdfService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Resolves a bundle key for {@code locale}, falling back to the key itself. */
    private String t(String key, Locale locale) {
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (NoSuchMessageException e) {
            return key;
        }
    }

    /** Maps a language tag (e.g. "es"/"en") to a Locale; null/blank => English fallback. */
    private Locale resolveLocale(String tag) {
        if (!StringUtils.hasText(tag)) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(tag);
        return StringUtils.hasText(locale.getLanguage()) ? locale : Locale.ENGLISH;
    }

    public byte[] generatePdf(ContractDto dto, String languageTag) throws IOException {
        Locale locale = resolveLocale(languageTag);
        String na = t("contract.na", locale);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDRectangle rect = page.getMediaBox();
                float width = rect.getWidth();
                float height = rect.getHeight();

                drawTitle(cs, width, height - 80f, "The Circle");
                drawSubtitle(cs, width, height - 110f, t("contract.title", locale));

                SignerDto p1 = dto.getPrimarySigner();
                SignerDto p2 = dto.getSecondarySigner();

                String name1 = p1 != null && p1.getFullName() != null ? p1.getFullName() : na;
                String name2 = p2 != null && p2.getFullName() != null ? p2.getFullName() : na;
                String addr1 = p1 != null && p1.getAddress() != null ? p1.getAddress() : na;
                String addr2 = p2 != null && p2.getAddress() != null ? p2.getAddress() : na;
                String id1 = p1 != null && p1.getIdNumber() != null ? p1.getIdNumber() : na;
                String id2 = p2 != null && p2.getIdNumber() != null ? p2.getIdNumber() : na;
                String type = typeLabel(dto.getType(), locale, na);
                String price = formatPrice(dto.getPrice(), na);

                float x = 60f;
                float y = height - 170f;
                float lineGap = 18f;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(x, y);
                cs.showText(t("contract.partyA", locale));
                cs.endText();
                y -= lineGap;

                y = drawLabelValue(cs, x, y, lineGap, t("contract.fullName", locale), name1);
                y = drawLabelValue(cs, x, y, lineGap, t("contract.idNumber", locale), id1);
                y = drawLabelValue(cs, x, y, lineGap, t("contract.address", locale), addr1);

                y -= lineGap;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(x, y);
                cs.showText(t("contract.partyB", locale));
                cs.endText();
                y -= lineGap;

                y = drawLabelValue(cs, x, y, lineGap, t("contract.fullName", locale), name2);
                y = drawLabelValue(cs, x, y, lineGap, t("contract.idNumber", locale), id2);
                y = drawLabelValue(cs, x, y, lineGap, t("contract.address", locale), addr2);

                y -= lineGap;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(x, y);
                cs.showText(t("contract.transaction", locale));
                cs.endText();
                y -= lineGap;

                y = drawLabelValue(cs, x, y, lineGap, t("contract.type", locale), type);
                y = drawLabelValue(cs, x, y, lineGap, t("contract.price", locale), price);
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

    /** Localized label for the contract type (mirrors the frontend mapping). */
    private String typeLabel(com.thecircle.contracts.dto.ContractType type, Locale locale, String na) {
        if (type == null) return na;
        String key = switch (type) {
            case SALE -> "contract.type.sale";
            case RENT -> "contract.type.rent";
            case CESSION_TEMPORARY -> "contract.type.loan";
            case CESSION_PERMANENT -> "contract.type.donation";
        };
        return t(key, locale);
    }

    /**
     * Contract amounts stay in EUR: the price is the legally agreed transaction
     * value, independent of the reader's display-currency preference.
     */
    private String formatPrice(BigDecimal price, String na) {
        if (price == null) return na;
        return price.toPlainString() + " EUR";
    }
}
