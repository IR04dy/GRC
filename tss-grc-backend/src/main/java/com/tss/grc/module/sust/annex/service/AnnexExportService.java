package com.tss.grc.module.sust.annex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import com.tss.grc.module.sust.annex.entity.SustainabilityReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * S-I — PDF (OpenPDF, Arabic-capable via the shared {@link PdfFonts}) + CSV
 * export of a persisted {@link SustainabilityReport}. Renders the FROZEN
 * content snapshot (the approved markdown / JSON), so exports match the locked,
 * hashed content rather than re-querying live data.
 */
@Service
@RequiredArgsConstructor
public class AnnexExportService {

    private final ObjectMapper objectMapper;
    private final OrganisationRepository orgRepo;

    /** Branded PDF: cover hero + the frozen markdown annex, line by line. */
    public byte[] pdf(SustainabilityReport r) {
        return pdf(r, "en");
    }

    /** @param lang pass "ar" to flip the document to RTL run direction (P3).
     *  Default "en" leaves output byte-identical to the pre-lang behaviour. */
    public byte[] pdf(SustainabilityReport r, String lang) {
        boolean ar = "ar".equalsIgnoreCase(lang);
        int rd = PdfTheme.runDir(ar);
        Organisation o = orgRepo.findById(r.getOrganisationId()).orElse(null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(rd);
            writer.setPageEvent(PdfTheme.professionalFooter(
                    r.getReportCode() == null ? "Sustainability Annex" : r.getReportCode(), "Internal"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "Sustainability Annex";
            cv.eyebrowAr = "ملحق الاستدامة";
            cv.code = r.getReportCode();
            cv.titleEn = r.getTitleEn();
            cv.titleAr = r.getTitleAr();
            cv.orgNameEn = o == null ? null : o.getNameEn();
            cv.orgNameAr = o == null ? null : o.getNameAr();
            cv.subtitle = "Status: " + r.getStatus()
                    + (r.getContentHash() != null ? "   ·   SHA-256 " + r.getContentHash().substring(0, 12) + "…" : "");
            cv.classification = "Internal";
            PdfTheme.coverHero(doc, cv);

            String md = r.getContentMarkdown() == null ? "(empty)" : r.getContentMarkdown();
            for (String line : md.split("\n", -1)) {
                Paragraph p = new Paragraph(line.isEmpty() ? " " : line, PdfFonts.body(9));
                p.setLeading(12f);
                doc.add(p);
            }
            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Annex PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    /** Flat CSV of the frozen figures (parsed from the JSON snapshot). */
    public String csv(SustainabilityReport r) {
        StringBuilder sb = new StringBuilder("section,metric,value\n");
        sb.append(row("meta", "reportCode", r.getReportCode()));
        sb.append(row("meta", "status", r.getStatus()));
        sb.append(row("meta", "version", r.getVersion()));
        sb.append(row("meta", "contentHash", r.getContentHash()));
        try {
            JsonNode n = objectMapper.readTree(r.getContentJson() == null ? "{}" : r.getContentJson());
            sb.append(row("annex", "reportingYear", text(n, "reportingYear")));
            sb.append(row("annex", "climateRiskCount", text(n, "climateRiskCount")));
            sb.append(row("annex", "cmaArticleCount", text(n, "cmaArticleCount")));
            sb.append(row("annex", "tcfdItemCount", text(n, "tcfdItemCount")));
            sb.append(row("annex", "sgiCommitmentCount", text(n, "sgiCommitmentCount")));
            JsonNode ghg = n.get("ghg");
            if (ghg != null) {
                sb.append(row("ghg", "scope1", text(ghg, "scope1")));
                sb.append(row("ghg", "scope2", text(ghg, "scope2")));
                sb.append(row("ghg", "scope3", text(ghg, "scope3")));
                sb.append(row("ghg", "total", text(ghg, "total")));
                sb.append(row("ghg", "scope3Coverage", text(ghg, "scope3Coverage")));
            }
        } catch (Exception e) {
            throw new ExportException("CSV", "Annex CSV generation failed", e);
        }
        return sb.toString();
    }

    /**
     * HTML export — the frozen snapshot when present, otherwise a branded RTL fallback wrapping the
     * markdown + metadata. Reports produced by the generic generator store only a markdown snapshot
     * (content_html null), which previously exported the literal "(empty)"; this makes every report
     * export a real HTML document.
     */
    public String html(SustainabilityReport r) {
        String stored = r.getContentHtml();
        if (stored != null && !stored.isBlank()) return stored;
        String title = r.getTitleEn() != null ? r.getTitleEn()
                     : r.getTitleAr() != null ? r.getTitleAr()
                     : (r.getReportCode() == null ? "Sustainability report" : r.getReportCode());
        String md = r.getContentMarkdown() == null ? "" : r.getContentMarkdown();
        return "<!doctype html>\n<html dir=\"rtl\" lang=\"ar\">\n<head><meta charset=\"utf-8\">"
            + "<title>" + htmlEsc(title) + "</title></head>\n<body>\n"
            + "<h1>" + htmlEsc(title) + "</h1>\n"
            + "<p><strong>Code:</strong> " + htmlEsc(nz(r.getReportCode()))
            + " &middot; <strong>Type:</strong> " + htmlEsc(nz(r.getReportType()))
            + " &middot; <strong>Status:</strong> " + htmlEsc(nz(r.getStatus()))
            + " &middot; <strong>Version:</strong> " + htmlEsc(nz(r.getVersion())) + "</p>\n"
            + "<pre style=\"white-space:pre-wrap;font-family:inherit\">" + htmlEsc(md) + "</pre>\n"
            + "</body>\n</html>\n";
    }

    /**
     * JSON export — the frozen snapshot when present, otherwise a metadata object derived from the
     * report fields (never the literal "(empty)").
     */
    public String json(SustainabilityReport r) {
        String stored = r.getContentJson();
        if (stored != null && !stored.isBlank()) return stored;
        try {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("reportCode", r.getReportCode());
            m.put("reportType", r.getReportType());
            m.put("status", r.getStatus());
            m.put("version", r.getVersion());
            m.put("contentHash", r.getContentHash());
            m.put("hasMarkdown", r.getContentMarkdown() != null && !r.getContentMarkdown().isBlank());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
        } catch (Exception e) {
            throw new ExportException("JSON", "Annex JSON generation failed", e);
        }
    }

    private static String nz(Object v) { return v == null ? "" : String.valueOf(v); }

    private static String htmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String row(String section, String metric, Object value) {
        return esc(section) + "," + esc(metric) + "," + esc(value == null ? "" : String.valueOf(value)) + "\n";
    }
    private static String esc(String s) {
        if (s == null) return "";
        // CSV/spreadsheet formula-injection guard (OWASP): a cell beginning with = + - @
        // (or tab/CR) executes as a formula in Excel/Sheets; prefix with a single quote so
        // the annex value stays literal text when a regulator opens the CSV.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
    private static String text(JsonNode n, String field) {
        JsonNode f = n.get(field);
        return f == null || f.isNull() ? "" : f.asText();
    }
}
