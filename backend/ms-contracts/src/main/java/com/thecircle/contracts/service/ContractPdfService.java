package com.thecircle.contracts.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.thecircle.contracts.dto.ContractDto;
import com.thecircle.contracts.dto.ContractType;
import com.thecircle.contracts.dto.SignerDto;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Renders a contract as a localized, eIDAS/GDPR-flavoured PDF.
 *
 * <p>The document is a Thymeleaf HTML template ({@code templates/contract.html},
 * i18n through {@code #{...}}) turned into a PDF by openhtmltopdf. The signature
 * zone is data-driven: each party's block shows the custom digital signature
 * (logo + timestamp + name + ID) once that party's signed timestamp is present,
 * otherwise a "pending" placeholder. Both blocks sit side by side, so a fully
 * signed contract shows the two signatures next to each other.
 */
@Service
public class ContractPdfService {

    private static final String TEMPLATE = "contract";
    private static final String BODY_TEMPLATE = "contract-body";
    private static final Locale SPANISH = Locale.forLanguageTag("es");
    private static final String FONT_FAMILY = "Contract";
    // Carlito: libre, metric-compatible with Calibri (SIL OFL). Four variants so
    // bold/italic render as real glyphs, not faux styles.
    private static final String FONT_REGULAR = "fonts/Carlito-Regular.ttf";
    private static final String FONT_BOLD = "fonts/Carlito-Bold.ttf";
    private static final String FONT_ITALIC = "fonts/Carlito-Italic.ttf";
    private static final String FONT_BOLD_ITALIC = "fonts/Carlito-BoldItalic.ttf";
    private static final String LOGO_PATH = "img/full-logo.png";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SpringTemplateEngine templateEngine;
    private final String logoDataUri;

    public ContractPdfService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.logoDataUri = loadLogoDataUri();
    }

    /**
     * Renders a single, bilingual PDF. The legal body is rendered twice — once in
     * Spanish and once in English — so neither party's binding text depends on who
     * signs last; {@code languageTag} only decides which language is presented
     * first. A single shared signature zone carries both signatures.
     */
    public byte[] generatePdf(ContractDto dto, String languageTag) throws IOException {
        boolean englishFirst = "en".equals(resolveLocale(languageTag).getLanguage());

        String esBody = templateEngine.process(BODY_TEMPLATE, buildBodyContext(dto, SPANISH));
        String enBody = templateEngine.process(BODY_TEMPLATE, buildBodyContext(dto, Locale.ENGLISH));

        Context shell = buildShellContext(dto);
        shell.setVariable("firstBody", englishFirst ? enBody : esBody);
        shell.setVariable("secondBody", englishFirst ? esBody : enBody);

        return htmlToPdf(templateEngine.process(TEMPLATE, shell));
    }

    /** The localized legal body: parties, recitals, clauses and the operation table. */
    private Context buildBodyContext(ContractDto dto, Locale locale) {
        Context ctx = new Context(locale);
        String na = "[N/A]";

        // Party A = primary signer = receiver; Party B = secondary signer = owner.
        SignerDto a = dto.getPrimarySigner();
        SignerDto b = dto.getSecondarySigner();

        ctx.setVariable("typeKey", typeKey(dto.getType()));
        ctx.setVariable("typeNounKey", typeNounKey(dto.getType()));
        ctx.setVariable("itemLabel", text(dto.getItemId(), na));
        ctx.setVariable("priceText", formatAmount(dto));
        ctx.setVariable("startDate", dto.getStartDate() != null ? dto.getStartDate().format(DAY) : null);
        ctx.setVariable("endDate", dto.getEndDate() != null ? dto.getEndDate().format(DAY) : null);

        ctx.setVariable("aName", signerName(a, na));
        ctx.setVariable("aDni", signerId(a, na));
        ctx.setVariable("aAddress", signerAddress(a, na));
        ctx.setVariable("bName", signerName(b, na));
        ctx.setVariable("bDni", signerId(b, na));
        ctx.setVariable("bAddress", signerAddress(b, na));

        return ctx;
    }

    /** The language-neutral shell: logo header, metadata and the shared signature zone. */
    private Context buildShellContext(ContractDto dto) {
        Context ctx = new Context();
        String na = "[N/A]";

        SignerDto a = dto.getPrimarySigner();
        SignerDto b = dto.getSecondarySigner();

        ctx.setVariable("logoDataUri", logoDataUri);
        ctx.setVariable("contractRef", text(dto.getContractId(), na));
        ctx.setVariable("issueDate", LocalDateTime.now().format(TS));

        ctx.setVariable("aName", signerName(a, na));
        ctx.setVariable("aDni", signerId(a, na));
        ctx.setVariable("bName", signerName(b, na));
        ctx.setVariable("bDni", signerId(b, na));

        LocalDateTime aSignedAt = dto.getReceiverSignedAt();
        LocalDateTime bSignedAt = dto.getOwnerSignedAt();
        ctx.setVariable("aSigned", aSignedAt != null);
        ctx.setVariable("aSignedOn", aSignedAt != null ? aSignedAt.format(TS) : null);
        ctx.setVariable("bSigned", bSignedAt != null);
        ctx.setVariable("bSignedOn", bSignedAt != null ? bSignedAt.format(TS) : null);

        return ctx;
    }

    private byte[] htmlToPdf(String html) throws IOException {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.Jsoup.parse(html);
        jsoupDoc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // The runtime container ships no system fonts, so the embeddable bundled
            // font is the only one available; the template's font-family points at it.
            builder.useFont(() -> openClasspath(FONT_REGULAR), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> openClasspath(FONT_BOLD), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.useFont(() -> openClasspath(FONT_ITALIC), FONT_FAMILY, 400, FontStyle.ITALIC, true);
            builder.useFont(() -> openClasspath(FONT_BOLD_ITALIC), FONT_FAMILY, 700, FontStyle.ITALIC, true);
            builder.withW3cDocument(w3cDoc, "");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    private String loadLogoDataUri() {
        try (InputStream in = openClasspath(LOGO_PATH)) {
            byte[] bytes = in.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            // A missing logo must not break contract generation; render without it.
            return "";
        }
    }

    private InputStream openClasspath(String path) {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("Missing bundled resource: " + path, e);
        }
    }

    private String typeKey(ContractType type) {
        if (type == null) return "contract.type.sale";
        return switch (type) {
            case SALE -> "contract.type.sale";
            case RENT -> "contract.type.rent";
            case CESSION_TEMPORARY -> "contract.type.loan";
            case CESSION_PERMANENT -> "contract.type.donation";
        };
    }

    private String typeNounKey(ContractType type) {
        if (type == null) return "contract.typeNoun.sale";
        return switch (type) {
            case SALE -> "contract.typeNoun.sale";
            case RENT -> "contract.typeNoun.rent";
            case CESSION_TEMPORARY -> "contract.typeNoun.loan";
            case CESSION_PERMANENT -> "contract.typeNoun.donation";
        };
    }

    /**
     * Contract amounts stay in EUR: the price is the legally agreed transaction
     * value, independent of the reader's display-currency preference. Falls back
     * to the monthly rent when no outright price is set (rentals).
     */
    private String formatAmount(ContractDto dto) {
        BigDecimal amount = dto.getPrice() != null ? dto.getPrice() : dto.getMonthlyRent();
        if (amount == null) return "[N/A]";
        return amount.toPlainString() + " EUR";
    }

    private String signerName(SignerDto s, String na) {
        return s != null && StringUtils.hasText(s.getFullName()) ? s.getFullName() : na;
    }

    private String signerId(SignerDto s, String na) {
        return s != null && StringUtils.hasText(s.getIdNumber()) ? s.getIdNumber() : na;
    }

    private String signerAddress(SignerDto s, String na) {
        return s != null && StringUtils.hasText(s.getAddress()) ? s.getAddress() : na;
    }

    private String text(String value, String na) {
        return StringUtils.hasText(value) ? value : na;
    }

    /** Maps a language tag (e.g. "es"/"en") to a Locale; null/blank => English fallback. */
    private Locale resolveLocale(String tag) {
        if (!StringUtils.hasText(tag)) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(tag);
        return StringUtils.hasText(locale.getLanguage()) ? locale : Locale.ENGLISH;
    }
}
