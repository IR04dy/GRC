package com.tss.grc.module.sust.climate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import com.tss.grc.module.reporting.service.XlsxStyler;
import com.tss.grc.module.sust.climate.entity.ClimateReport;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * PDF (OpenPDF, Arabic-capable via {@link PdfFonts}) + Excel (POI) export of a
 * persisted {@link ClimateReport} snapshot — S-G. Renders the LOCKED JSON
 * content (never re-queries live data), so an exported PDF/XLSX matches exactly
 * the approved figures + hash.
 */
@Service
@RequiredArgsConstructor
public class ClimateReportExportService {

    private final ObjectMapper objectMapper;
    private final OrganisationRepository orgRepo;

    @SuppressWarnings("unchecked")
    private Map<String, Object> content(ClimateReport r) {
        try {
            return objectMapper.readValue(r.getContentJson() == null ? "{}" : r.getContentJson(), Map.class);
        } catch (Exception e) {
            throw new ExportException("PDF", "Failed to parse report content JSON", e);
        }
    }

    // ══════════════════════════ PDF ══════════════════════════

    public byte[] pdf(ClimateReport report, boolean ar) {
        Map<String, Object> c = content(report);
        Organisation o = orgRepo.findById(report.getOrganisationId()).orElse(null);

        Map<String, Object> ghg = asMap(c.get("ghgInventory"));
        Map<String, Object> ev  = asMap(c.get("evidenceIndex"));
        List<Map<String, Object>> checklist = asList(c.get("tcfdChecklist"));
        List<Map<String, Object>> capa      = asList(c.get("gapsCapa"));
        List<Map<String, Object>> evItems   = asList(ev.get("items"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(PdfTheme.runDir(ar));   // P0-2: RTL for Arabic climate report
            writer.setPageEvent(PdfTheme.professionalFooter(report.getReportCode(), "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = "IFRS S2 / TCFD Climate Report";
            cv.eyebrowAr = "تقرير المناخ الإفصاحي";
            cv.code = report.getReportCode();
            cv.titleEn = report.getTitleEn();
            cv.titleAr = report.getTitleAr();
            cv.orgNameEn = o == null ? null : o.getNameEn();
            cv.orgNameAr = o == null ? null : o.getNameAr();
            cv.subtitle = "Status: " + report.getStatus()
                    + (report.getContentHash() != null ? "   ·   SHA-256 " + report.getContentHash().substring(0, 12) + "…" : "");
            cv.classification = "Confidential";
            PdfTheme.coverHero(doc, cv);

            PdfTheme.kpiStrip(doc, List.of(
                    new PdfTheme.Kpi("GHG total tCO₂e", str(ghg.get("grandTotal")), "Scope 1+2+3"),
                    new PdfTheme.Kpi("TCFD items", String.valueOf(checklist.size()), "4-pillar checklist"),
                    new PdfTheme.Kpi("Open CAPA", String.valueOf(capa.size()), "gaps + actions"),
                    new PdfTheme.Kpi("Evidence", str(ev.get("itemCount")), "linked items")
            ));

            // TCFD checklist
            PdfTheme.sectionDivider(doc, "TCFD 4-Pillar Alignment Checklist", "قائمة التحقق من مواءمة TCFD");
            PdfPTable t1 = table(new float[]{1.3f, 0.8f, 1.3f, 3f, 2.5f}, ar,
                    "Pillar", "Code", "Status", "Recommendation", "Gap");
            for (Map<String, Object> e : checklist) {
                cell(t1, str(e.get("pillar"))); cell(t1, str(e.get("recommendationCode")));
                cell(t1, str(e.get("disclosureStatus"))); cell(t1, str(e.get("titleEn")));
                cell(t1, str(e.get("gapDescriptionEn")));
            }
            addOrEmpty(doc, t1, checklist.isEmpty());

            // GHG inventory
            PdfTheme.sectionDivider(doc, "GHG Inventory", "جرد غازات الاحتباس الحراري");
            PdfPTable t2 = table(new float[]{2f, 2f}, ar, "Metric", "Value");
            kv(t2, "Scope 1 (tCO₂e)", str(ghg.get("scope1Total")));
            kv(t2, "Scope 2 (tCO₂e)", str(ghg.get("scope2Total")));
            kv(t2, "Scope 3 (tCO₂e)", str(ghg.get("scope3Total")));
            kv(t2, "Grand total (tCO₂e)", str(ghg.get("grandTotal")));
            kv(t2, "Scope-3 coverage %", str(ghg.get("scope3Coverage")));
            kv(t2, "Coverage basis", str(ghg.get("scope3CoverageBasis")));
            kv(t2, "Scope-3 partial flag", str(ghg.get("scope3PartialFlag")));
            doc.add(t2);

            // Gaps & CAPA
            PdfTheme.sectionDivider(doc, "Gaps & Corrective Actions", "الفجوات والإجراءات التصحيحية");
            PdfPTable t3 = table(new float[]{1.2f, 3f, 1.2f, 1f, 1.5f, 1.2f}, ar,
                    "Code", "Action", "Status", "Priority", "Source", "Target");
            for (Map<String, Object> e : capa) {
                cell(t3, str(e.get("actionCode"))); cell(t3, str(e.get("titleEn")));
                cell(t3, str(e.get("status"))); cell(t3, str(e.get("priority")));
                cell(t3, str(e.get("sourceType"))); cell(t3, str(e.get("targetDate")));
            }
            addOrEmpty(doc, t3, capa.isEmpty());

            // Evidence index
            PdfTheme.sectionDivider(doc, "Evidence Index", "فهرس الأدلة");
            PdfPTable t4 = table(new float[]{2.6f, 1.2f, 1.2f, 2f, 2.5f}, ar,
                    "Title", "Type", "Status", "File", "SHA-256");
            for (Map<String, Object> e : evItems) {
                cell(t4, str(e.get("titleEn"))); cell(t4, str(e.get("evidenceType")));
                cell(t4, str(e.get("status"))); cell(t4, str(e.get("fileName")));
                cell(t4, shortHash(e.get("sha256")));
            }
            addOrEmpty(doc, t4, evItems.isEmpty());

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Climate report PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private static PdfPTable table(float[] widths, boolean ar, String... headers) throws DocumentException {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setRunDirection(PdfTheme.runDir(ar));   // P0-2: RTL tables for Arabic
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, PdfFonts.header(9)));
            c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
            c.setBorderColor(PdfFonts.HAIRLINE);
            // RTL table cells mirror ALIGN semantics — visualStart, not alignStart.
            c.setHorizontalAlignment(PdfTheme.visualStart(ar,
                ar ? com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_RTL
                   : com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_DEFAULT));
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }
    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.body(8)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPadding(4);
        t.addCell(c);
    }
    private static void kv(PdfPTable t, String k, String v) { cell(t, k); cell(t, v); }
    private static void addOrEmpty(Document doc, PdfPTable t, boolean empty) throws DocumentException {
        doc.add(t);
        if (empty) {
            com.lowagie.text.Paragraph p = new com.lowagie.text.Paragraph("— none —", PdfFonts.small(9));
            p.setSpacingBefore(4);
            doc.add(p);
        }
    }

    // ══════════════════════════ Excel ══════════════════════════

    public byte[] xlsx(ClimateReport report) {
        Map<String, Object> c = content(report);
        Map<String, Object> ghg = asMap(c.get("ghgInventory"));
        Map<String, Object> ev  = asMap(c.get("evidenceIndex"));
        List<Map<String, Object>> checklist = asList(c.get("tcfdChecklist"));
        List<Map<String, Object>> capa      = asList(c.get("gapsCapa"));
        List<Map<String, Object>> evItems   = asList(ev.get("items"));

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle hdr = headerStyle(wb);

            Sheet summary = wb.createSheet("Summary");
            rowKv(summary, 0, "Report", str(report.getReportCode()), hdr);
            rowKv(summary, 1, "Reporting year", str(report.getReportingYear()), hdr);
            rowKv(summary, 2, "Status", str(report.getStatus()), hdr);
            rowKv(summary, 3, "Content SHA-256", str(report.getContentHash()), hdr);
            rowKv(summary, 4, "GHG grand total tCO2e", str(ghg.get("grandTotal")), hdr);
            rowKv(summary, 5, "Scope-3 coverage %", str(ghg.get("scope3Coverage")), hdr);
            rowKv(summary, 6, "TCFD checklist items", String.valueOf(checklist.size()), hdr);
            rowKv(summary, 7, "Open CAPA", String.valueOf(capa.size()), hdr);
            rowKv(summary, 8, "Evidence items", str(ev.get("itemCount")), hdr);
            summary.autoSizeColumn(0); summary.autoSizeColumn(1);

            sheet(wb, hdr, "TCFD Checklist",
                    new String[]{"Pillar", "Code", "Status", "Recommendation", "Gap"},
                    checklist, new String[]{"pillar", "recommendationCode", "disclosureStatus", "titleEn", "gapDescriptionEn"});
            sheet(wb, hdr, "GHG Inventory",
                    new String[]{"Metric", "Value"}, ghgRows(ghg), new String[]{"k", "v"});
            sheet(wb, hdr, "Gaps & CAPA",
                    new String[]{"Code", "Action", "Status", "Priority", "Source", "Target"},
                    capa, new String[]{"actionCode", "titleEn", "status", "priority", "sourceType", "targetDate"});
            sheet(wb, hdr, "Evidence Index",
                    new String[]{"Title", "Type", "Status", "File", "SHA-256"},
                    evItems, new String[]{"titleEn", "evidenceType", "status", "fileName", "sha256"});

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new ExportException("Excel", "Climate report Excel generation failed", e);
        }
    }

    private static List<Map<String, Object>> ghgRows(Map<String, Object> ghg) {
        return List.of(
                Map.of("k", "Scope 1 (tCO2e)", "v", str(ghg.get("scope1Total"))),
                Map.of("k", "Scope 2 (tCO2e)", "v", str(ghg.get("scope2Total"))),
                Map.of("k", "Scope 3 (tCO2e)", "v", str(ghg.get("scope3Total"))),
                Map.of("k", "Grand total (tCO2e)", "v", str(ghg.get("grandTotal"))),
                Map.of("k", "Scope-3 coverage %", "v", str(ghg.get("scope3Coverage"))),
                Map.of("k", "Coverage basis", "v", str(ghg.get("scope3CoverageBasis"))),
                Map.of("k", "Scope-3 partial flag", "v", str(ghg.get("scope3PartialFlag"))));
    }

    private static void sheet(Workbook wb, CellStyle hdr, String name, String[] headers,
                              List<Map<String, Object>> rows, String[] keys) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int i = 0; i < headers.length; i++) { Cell c = h.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hdr); }
        int r = 1;
        for (Map<String, Object> row : rows) {
            Row rr = s.createRow(r++);
            for (int i = 0; i < keys.length; i++) rr.createCell(i).setCellValue(str(row.get(keys[i])));
        }
        XlsxStyler.freezeAndFilter(s, headers.length, r - 1);   // P1: header freeze + auto-filter
        for (int i = 0; i < headers.length; i++) {
            s.autoSizeColumn(i);
            if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(s, i, 1, r - 1);
        }
    }

    private static void rowKv(Sheet s, int r, String k, String v, CellStyle hdr) {
        Row row = s.createRow(r);
        Cell kc = row.createCell(0); kc.setCellValue(k); kc.setCellStyle(hdr);
        row.createCell(1).setCellValue(v);
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    // ── small utils ──
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : Map.of(); }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object o) { return o instanceof List ? (List<Map<String, Object>>) o : List.of(); }
    private static String str(Object o) { return o == null ? "—" : String.valueOf(o); }
    private static String shortHash(Object o) {
        String s = o == null ? null : String.valueOf(o);
        return s == null || s.length() < 12 ? str(o) : s.substring(0, 12) + "…";
    }
}
