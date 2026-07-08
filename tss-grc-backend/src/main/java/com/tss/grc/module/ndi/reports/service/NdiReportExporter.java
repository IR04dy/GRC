package com.tss.grc.module.ndi.reports.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.reports.service.NdiReportService.ReportResult;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import com.tss.grc.module.reporting.service.XlsxStyler;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Binary exporters for NDI reports — slice 11 follow-up.
 *
 * <p>Takes the structured {@link ReportResult} the synthesizer already
 * produces and emits PDF, XLSX, or CSV bytes. PDF output uses the shared
 * {@link PdfTheme} primitives so an NDI submission package looks like the
 * BCP plan, crisis report, and board pack — same brand band, same KPI
 * tiles, same footer style.
 *
 * <p>The PDF layout is:
 * <ol>
 *   <li><b>Cover hero</b> — eyebrow ("NDI ASSESSMENT REPORT"), pretty
 *       title derived from the report type, org name, classification, and
 *       Hijri + Gregorian stamp.</li>
 *   <li><b>Summary KPI strip</b> — the first 4 entries from
 *       {@link ReportResult#summary()} rendered as warm-paper tiles. The
 *       rest of the summary continues as a key/value table below.</li>
 *   <li><b>Detail rows</b> — the union of all keys in
 *       {@link ReportResult#rows()} as a brand-themed table, with the
 *       page in A4 landscape so wide reports lay out cleanly.</li>
 * </ol>
 *
 * <p>Arabic in PDF: this exporter previously used {@code FontFactory.HELVETICA}
 * which has no Arabic glyphs — any Arabic data rendered as boxes. It now
 * routes through {@link PdfFonts} which loads an Arabic-capable TTF on
 * startup, so bilingual report keys/values render correctly.
 *
 * <p>XLSX/CSV emit-paths are unchanged — Excel + CSV both handle RTL natively
 * once the bytes are encoded UTF-8.
 */
@Component
@RequiredArgsConstructor
public class NdiReportExporter {

    private final OrganisationRepository orgs;

    public byte[] toXlsx(ReportResult result) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // P1: shared house header (brand teal) instead of the off-house grey.
            CellStyle headerStyle = XlsxStyler.headerStyle(wb);

            // ── Summary sheet ────────────────────────────────────────
            Sheet summary = wb.createSheet("Summary");
            Row h0 = summary.createRow(0);
            h0.createCell(0).setCellValue("Report type");
            h0.createCell(1).setCellValue(result.reportType());
            h0.getCell(0).setCellStyle(headerStyle);

            Row h1 = summary.createRow(1);
            h1.createCell(0).setCellValue("Generated at");
            h1.createCell(1).setCellValue(String.valueOf(result.generatedAt()));
            h1.getCell(0).setCellStyle(headerStyle);

            Row h2 = summary.createRow(2);
            h2.createCell(0).setCellValue("Cycle id");
            h2.createCell(1).setCellValue(String.valueOf(result.cycleId()));
            h2.getCell(0).setCellStyle(headerStyle);

            int row = 4;
            Row s0 = summary.createRow(row++);
            s0.createCell(0).setCellValue("Key");
            s0.createCell(1).setCellValue("Value");
            s0.getCell(0).setCellStyle(headerStyle);
            s0.getCell(1).setCellStyle(headerStyle);

            for (Map.Entry<String, Object> e : result.summary().entrySet()) {
                Row r = summary.createRow(row++);
                r.createCell(0).setCellValue(e.getKey());
                r.createCell(1).setCellValue(asString(e.getValue()));
            }
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            // ── Rows sheet ───────────────────────────────────────────
            if (!result.rows().isEmpty()) {
                Sheet rows = wb.createSheet("Rows");
                List<String> cols = collectColumns(result.rows());
                Row header = rows.createRow(0);
                for (int i = 0; i < cols.size(); i++) {
                    org.apache.poi.ss.usermodel.Cell c = header.createCell(i);
                    c.setCellValue(cols.get(i));
                    c.setCellStyle(headerStyle);
                }
                int r = 1;
                for (Map<String, Object> data : result.rows()) {
                    Row dataRow = rows.createRow(r++);
                    for (int i = 0; i < cols.size(); i++) {
                        Object v = data.get(cols.get(i));
                        dataRow.createCell(i).setCellValue(asString(v));
                    }
                }
                // P1: frozen header + auto-filter (was autosize-only) + status conditional formatting.
                XlsxStyler.freezeAndFilter(rows, cols.size(), r - 1);
                for (int i = 0; i < cols.size(); i++) {
                    rows.autoSizeColumn(i);
                    if (XlsxStyler.isStatusHeader(cols.get(i))) XlsxStyler.statusColors(rows, i, 1, r - 1);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] toPdf(ReportResult result) {
        return toPdf(result, "en");
    }

    /** @param lang pass "ar" to flip the document + tables to RTL run direction (P3).
     *  Default "en" leaves output byte-identical to the pre-lang behaviour. */
    public byte[] toPdf(ReportResult result, String lang) {
        boolean ar = "ar".equalsIgnoreCase(lang);
        int rd = PdfTheme.runDir(ar);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Landscape A4 — NDI report rows tend to be wide (control id,
            // domain, score, status, owner, evidence ref…). Letter portrait
            // would force horizontal overflow.
            Document doc = new Document(PageSize.A4.rotate(), 40, 40, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setRunDirection(rd);
            writer.setPageEvent(PdfTheme.professionalFooter(
                "NDI · " + prettyTitle(result.reportType()), "Internal"));
            doc.open();

            // ── Cover hero ───────────────────────────────────────────
            Organisation org = null;
            try {
                Long orgId = TenantContext.getCurrentTenant();
                if (orgId != null) org = orgs.findById(orgId).orElse(null);
            } catch (Exception ignored) { /* run outside tenant context — skip org */ }

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow   = "NDI Assessment Report";
            cv.eyebrowAr = "تقرير تقييم منظومة الحوكمة الرقمية الوطنية";
            cv.titleEn   = prettyTitle(result.reportType());
            cv.subtitle  = result.cycleId() == null ? "All cycles" : ("Cycle " + result.cycleId());
            cv.orgNameEn = org == null ? null : org.getNameEn();
            cv.orgNameAr = org == null ? null : org.getNameAr();
            cv.classification = "Internal";
            cv.generatedAt = result.generatedAt() == null ? null : result.generatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            PdfTheme.coverHero(doc, cv);

            // ── Summary KPI strip + key/value table ──────────────────
            if (result.summary() != null && !result.summary().isEmpty()) {
                List<Map.Entry<String, Object>> entries = new ArrayList<>(result.summary().entrySet());
                List<PdfTheme.Kpi> kpis = new ArrayList<>();
                int kpiCount = Math.min(4, entries.size());
                for (int i = 0; i < kpiCount; i++) {
                    Map.Entry<String, Object> e = entries.get(i);
                    kpis.add(new PdfTheme.Kpi(prettyKey(e.getKey()), asString(e.getValue()), ""));
                }
                PdfTheme.kpiStrip(doc, kpis);

                // Remaining keys as a key/value list (only if more than 4)
                if (entries.size() > 4) {
                    Paragraph head = new Paragraph("Summary detail", PdfFonts.bold(12));
                    head.setSpacingBefore(6);
                    head.setSpacingAfter(8);
                    doc.add(head);

                    PdfPTable t = new PdfPTable(2);
                    t.setWidthPercentage(60);
                    t.setRunDirection(rd);
                    t.setHorizontalAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    try { t.setWidths(new float[] {2, 3}); } catch (DocumentException ignored) {}
                    headerCell(t, "Key");
                    headerCell(t, "Value");
                    for (int i = 4; i < entries.size(); i++) {
                        Map.Entry<String, Object> e = entries.get(i);
                        cell(t, prettyKey(e.getKey()));
                        cell(t, asString(e.getValue()));
                    }
                    doc.add(t);
                }
            }

            // ── Detail rows ──────────────────────────────────────────
            if (!result.rows().isEmpty()) {
                PdfTheme.sectionDivider(doc,
                    "Detail Rows (" + result.rows().size() + ")",
                    "تفاصيل النتائج");

                List<String> cols = collectColumns(result.rows());
                PdfPTable table = new PdfPTable(cols.size());
                table.setWidthPercentage(100);
                table.setRunDirection(rd);
                for (String c : cols) headerCell(table, prettyKey(c));
                for (Map<String, Object> data : result.rows()) {
                    for (String c : cols) cell(table, asString(data.get(c)));
                }
                doc.add(table);
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ExportException("PDF", "NDI report PDF export failed", ex);
        }
    }

    public byte[] toCsv(ReportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(result.reportType()).append('\n');
        sb.append("# generated_at,").append(result.generatedAt()).append('\n');
        if (result.cycleId() != null) sb.append("# cycle_id,").append(result.cycleId()).append('\n');
        if (!result.rows().isEmpty()) {
            List<String> cols = collectColumns(result.rows());
            sb.append(String.join(",", cols.stream().map(this::csvEscape).toList())).append('\n');
            for (Map<String, Object> data : result.rows()) {
                List<String> cells = new ArrayList<>();
                for (String c : cols) cells.add(csvEscape(asString(data.get(c))));
                sb.append(String.join(",", cells)).append('\n');
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static List<String> collectColumns(List<Map<String, Object>> rows) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) seen.addAll(r.keySet());
        return new ArrayList<>(seen);
    }

    private static String asString(Object v) {
        if (v == null) return "";
        return String.valueOf(v);
    }

    private static String prettyTitle(String reportType) {
        if (reportType == null) return "Report";
        return "NDI Report — " + reportType.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /** Converts SCREAMING_SNAKE_CASE to Title Case for column headers + KPI labels. */
    private static String prettyKey(String key) {
        if (key == null || key.isBlank()) return key;
        String[] parts = key.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (i > 0) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static void headerCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(9)));
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(6); c.setPaddingBottom(6);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null || text.isEmpty() ? "—" : text,
            PdfFonts.body(9)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(5); c.setPaddingBottom(5);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        // Defend against CSV/spreadsheet formula injection (OWASP): NDI report rows are
        // attacker-influenceable free text; a cell beginning with = + - @ (or tab/CR) is
        // executed as a formula when a regulator opens the file in Excel/Sheets. Prefix
        // such cells with a single quote so the value stays literal text.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
