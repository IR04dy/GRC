package com.tss.grc.module.reporting.service;

// Explicit imports — wildcard com.lowagie.text.* collides with
// org.apache.poi.ss.usermodel.* on Cell and Row.
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bilingual operational-report export service (Q1) using OpenPDF.
 *
 * <p>Produces the three core register exports the platform offers as
 * download buttons across the UI: Risk Register, Findings Aging, and
 * KRI Dashboard. Each method emits both a leadership-grade PDF (cover
 * hero + KPI strip + branded table + classification footer) and a
 * tabular XLSX with live formulas for hands-on analysis.
 *
 * <p>The PDF side uses the shared {@link PdfTheme} primitives so every
 * report looks identical to the BCP/Crisis/NDI exporters and the board
 * pack — one brand identity across the whole platform's printed output.
 *
 * <p>Locale handling: pass {@code lang = "ar"} to render Arabic column
 * headers and the Arabic organisation name on the cover. The exporter
 * keeps the document run direction LTR throughout; Arabic glyphs are
 * shaped at the run level by the bundled TTF so Latin codes (R-00012)
 * and Arabic headers can coexist without flipping.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriMeasurementRepository measRepo;

    // ── Risk Register ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] riskRegisterPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();

        // KPI counts — surfaced on the cover so a reader sees the headline
        // numbers before paging into the row dump.
        long total    = risks.size();
        long critical = risks.stream().filter(r -> "CRITICAL".equalsIgnoreCase(r.getRiskLevel())).count();
        long high     = risks.stream().filter(r -> "HIGH".equalsIgnoreCase(r.getRiskLevel())).count();
        long open     = risks.stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus())).count();

        return buildReportPdf(o, ar,
            "Risk Register",      "سجل المخاطر",
            "Enterprise Risk Register", "سجل المخاطر المؤسسية",
            List.of(
                new PdfTheme.Kpi("Total risks", String.valueOf(total), "All states"),
                new PdfTheme.Kpi(ar ? "حرجة" : "Critical", String.valueOf(critical), "Need attention"),
                new PdfTheme.Kpi(ar ? "عالية" : "High",   String.valueOf(high), ""),
                new PdfTheme.Kpi(ar ? "مفتوحة" : "Open",  String.valueOf(open), "")
            ),
            (doc) -> {
                float[] widths = new float[]{ 1.2f, 4f, 1.6f, 1f, 1f, 1f, 1.5f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                String[] hdr = ar
                    ? new String[]{ "الرقم", "المخاطرة", "الفئة", "الاحتمال", "الأثر", "النتيجة", "الحالة" }
                    : new String[]{ "Code",  "Risk",     "Category", "L",       "I",       "Score",   "Status" };
                for (String h : hdr) t.addCell(headerCell(h));
                for (Risk r : risks) {
                    String name = ar && r.getTitleAr() != null ? r.getTitleAr() : r.getTitleEn();
                    cell(t, "R-" + String.format("%05d", r.getId()));
                    cell(t, safe(name));
                    cell(t, "");
                    cell(t, String.valueOf(r.getInherentLikelihood()));
                    cell(t, String.valueOf(r.getInherentImpact()));
                    cell(t, String.valueOf(r.getInherentScore()));
                    cell(t, safe(r.getStatus()));
                }
                doc.add(t);
            });
    }

    // ── Findings aging ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] findingsAgingPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        LocalDate today = LocalDate.now();

        // KPI counts — total, open, past-30d, past-90d (escalation tier triggers)
        long total = findings.size();
        long open  = findings.stream().filter(f -> !"CLOSED".equalsIgnoreCase(f.getStatus())).count();
        long past30 = 0, past90 = 0;
        for (AuditFinding f : findings) {
            if ("CLOSED".equalsIgnoreCase(f.getStatus())) continue;
            LocalDateTime created = f.getCreatedAt();
            long days = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            if (days >= 90) past90++;
            else if (days >= 30) past30++;
        }
        final long past30Final = past30;
        final long past90Final = past90;

        return buildReportPdf(o, ar,
            "Audit Findings",                "ملاحظات التدقيق",
            "Findings Aging Report",         "تقرير تقادم الملاحظات",
            List.of(
                new PdfTheme.Kpi(ar ? "إجمالي" : "Total",     String.valueOf(total), ""),
                new PdfTheme.Kpi(ar ? "مفتوحة" : "Open",      String.valueOf(open),  ""),
                new PdfTheme.Kpi(ar ? "متجاوزة 30 يوم" : "Past 30d", String.valueOf(past30Final), ar ? "تصعيد مدير" : "Manager tier"),
                new PdfTheme.Kpi(ar ? "متجاوزة 90 يوم" : "Past 90d", String.valueOf(past90Final), ar ? "تصعيد تنفيذي" : "Exec tier")
            ),
            (doc) -> {
                float[] widths = new float[]{ 4f, 1.4f, 1.2f, 1f, 1.4f, 1.6f, 1.4f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                String[] hdr = ar
                    ? new String[]{ "الملاحظة", "الأهمية", "المالك", "الأيام", "التصعيد", "مرحلة CAPA", "الحالة" }
                    : new String[]{ "Finding",  "Severity", "Owner",  "Days",   "Tier",     "CAPA Stage",  "Status" };
                for (String h : hdr) t.addCell(headerCell(h));

                for (AuditFinding f : findings) {
                    LocalDateTime created = f.getCreatedAt();
                    long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                    String tier = daysOpen >= 90 ? "EXEC" : daysOpen >= 60 ? "DIRECTOR" : daysOpen >= 30 ? "MANAGER" : "ON_TRACK";
                    cell(t, safe(f.getTitle()));
                    cell(t, safe(f.getSeverity()));
                    cell(t, safe(f.getOwner()));
                    cell(t, String.valueOf(daysOpen));
                    cell(t, tier);
                    cell(t, safe(f.getCapaStage()));
                    cell(t, safe(f.getStatus()));
                }
                doc.add(t);
            });
    }

    // ── KRI dashboard ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] kriDashboardPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(org);

        // QUAL-BCMP-006: one query for each KRI's latest reading instead of a
        // per-KRI N+1 loop. Key present ⇒ a reading exists; value [0]=band (may
        // be null), [1]=readingValue. Mirrors findFirstByKriIdOrderByReadingAtDesc.
        final Map<Long, Object[]> latestByKri = latestReadingByKri(kris);

        // Band counts — green/amber/red/no-data — for the cover KPI strip
        long green = 0, amber = 0, red = 0, noData = 0;
        for (KriDefinition k : kris) {
            Object[] latest = latestByKri.get(k.getId());
            String band = latest == null ? null : (String) latest[0];
            if (band == null) noData++;
            else if ("GREEN".equalsIgnoreCase(band)) green++;
            else if ("AMBER".equalsIgnoreCase(band)) amber++;
            else if ("RED".equalsIgnoreCase(band)) red++;
        }
        final long greenF = green, amberF = amber, redF = red, noDataF = noData;

        return buildReportPdf(o, ar,
            "Key Risk Indicators",         "مؤشرات المخاطر الرئيسية",
            "KRI Dashboard",               "لوحة مؤشرات المخاطر",
            List.of(
                new PdfTheme.Kpi(ar ? "أخضر"  : "Green",  String.valueOf(greenF), ""),
                new PdfTheme.Kpi(ar ? "أصفر"  : "Amber",  String.valueOf(amberF), ""),
                new PdfTheme.Kpi(ar ? "أحمر"  : "Red",    String.valueOf(redF),   ""),
                new PdfTheme.Kpi(ar ? "بدون بيانات" : "No data", String.valueOf(noDataF), "")
            ),
            (doc) -> {
                float[] widths = new float[]{ 1.3f, 4f, 1f, 1.3f, 1.3f, 1.3f, 1.6f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                String[] hdr = ar
                    ? new String[]{ "الرمز", "اسم المؤشر", "الحالة", "آخر قراءة", "حد أخضر", "حد أصفر", "المالك" }
                    : new String[]{ "Code",  "KRI",        "Band",    "Latest",     "Green ≤", "Amber ≤", "Owner" };
                for (String h : hdr) t.addCell(headerCell(h));

                for (KriDefinition k : kris) {
                    Object[] latest = latestByKri.get(k.getId());
                    String name = ar && k.getTitleAr() != null ? k.getTitleAr() : k.getTitleEn();
                    cell(t, safe(k.getKriCode()));
                    cell(t, safe(name));
                    cell(t, latest != null ? safe((String) latest[0]) : "—");
                    cell(t, latest != null ? ((BigDecimal) latest[1]).toPlainString() : "—");
                    cell(t, k.getGreenThreshold() != null ? k.getGreenThreshold().toPlainString() : "—");
                    cell(t, k.getAmberThreshold() != null ? k.getAmberThreshold().toPlainString() : "—");
                    cell(t, k.getOwnerUserId() != null ? String.valueOf(k.getOwnerUserId()) : "—");
                }
                doc.add(t);
            });
    }

    // ── Shared report skeleton ─────────────────────────────────────────────

    /**
     * Functional shape so each report type only has to describe its own
     * table rendering — the cover hero, KPI strip, footer, and lifecycle
     * are identical across all three reports and live here once.
     */
    @FunctionalInterface
    private interface TableRenderer {
        void render(Document doc) throws DocumentException;
    }

    private byte[] buildReportPdf(Organisation o, boolean ar,
                                  String eyebrowEn, String eyebrowAr,
                                  String titleEn, String titleAr,
                                  List<PdfTheme.Kpi> kpis,
                                  TableRenderer table) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Landscape — register dumps tend to be wide (code + title + L/I/score
        // + status + …). Portrait would force horizontal overflow.
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(PdfTheme.professionalFooter(eyebrowEn, "Internal"));
            doc.open();

            // Cover
            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = eyebrowEn;
            cv.eyebrowAr = eyebrowAr;
            cv.titleEn = titleEn;
            cv.titleAr = titleAr;
            cv.orgNameEn = o == null ? null : o.getNameEn();
            cv.orgNameAr = o == null ? null : o.getNameAr();
            cv.classification = "Internal";
            PdfTheme.coverHero(doc, cv);

            // KPI strip
            if (kpis != null && !kpis.isEmpty()) {
                PdfTheme.kpiStrip(doc, new ArrayList<>(kpis));
            }

            // Table
            table.render(doc);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(10)));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(6); c.setPaddingBottom(6);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        return c;
    }
    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.body(9)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(5); c.setPaddingBottom(5);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }
    private static String safe(String s) { return s == null ? "—" : s; }

    /**
     * Latest reading per KRI in one query (QUAL-BCMP-006) — collapses the N+1
     * {@code findFirstByKriIdOrderByReadingAtDesc} loop shared by the KRI PDF and
     * XLSX exporters. Returns a map keyed by KRI id; presence of a key mirrors
     * {@code findFirstByKriIdOrderByReadingAtDesc(id).isPresent()}, value
     * {@code [0]=band} (nullable), {@code [1]=readingValue}.
     */
    private Map<Long, Object[]> latestReadingByKri(List<KriDefinition> kris) {
        Map<Long, Object[]> out = new HashMap<>();
        List<Long> kriIds = kris.stream().map(KriDefinition::getId).toList();
        if (kriIds.isEmpty()) return out;
        for (Object[] row : measRepo.findLatestPerKriIn(kriIds)) {
            out.put((Long) row[0], new Object[]{ row[1], row[2] });
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel exports (Q2) — same datasets, .xlsx with formulas + auto-sized cols
    // ════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] riskRegisterXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Risk Register");
            CellStyle hdrStyle = headerStyle(wb);
            CellStyle wrapStyle = wrapStyle(wb);

            String[] headers = { "Code", "Title (EN)", "Title (AR)", "Status",
                "Inherent L", "Inherent I", "Inherent Score",
                "Residual L", "Residual I", "Residual Score (formula)" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }

            int rowIdx = 1;
            for (Risk r : risks) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("R-" + String.format("%05d", r.getId()));
                Cell t1 = row.createCell(1); t1.setCellValue(safe(r.getTitleEn())); t1.setCellStyle(wrapStyle);
                Cell t2 = row.createCell(2); t2.setCellValue(safe(r.getTitleAr())); t2.setCellStyle(wrapStyle);
                row.createCell(3).setCellValue(safe(r.getStatus()));
                if (r.getInherentLikelihood() != null) row.createCell(4).setCellValue(r.getInherentLikelihood());
                if (r.getInherentImpact()     != null) row.createCell(5).setCellValue(r.getInherentImpact());
                if (r.getInherentScore()      != null) row.createCell(6).setCellValue(r.getInherentScore());
                if (r.getResidualLikelihood() != null) row.createCell(7).setCellValue(r.getResidualLikelihood());
                if (r.getResidualImpact()     != null) row.createCell(8).setCellValue(r.getResidualImpact());
                // Residual score as a formula so users can edit L/I and have score recompute
                Cell residualScoreCell = row.createCell(9);
                residualScoreCell.setCellFormula("IF(AND(ISNUMBER(H" + (rowIdx) + "),ISNUMBER(I" + (rowIdx) + ")),H" + (rowIdx) + "*I" + (rowIdx) + ",\"\")");
            }

            // Total + count formulas at bottom (like a spreadsheet user would)
            int totalRow = rowIdx + 1;
            Row tr = sheet.createRow(totalRow);
            Cell label = tr.createCell(5); label.setCellValue("Total / Avg →");
            label.setCellStyle(hdrStyle);
            Cell sumCell = tr.createCell(6);
            sumCell.setCellFormula("SUM(G2:G" + rowIdx + ")");
            Cell avgCell = tr.createCell(9);
            avgCell.setCellFormula("IFERROR(AVERAGE(J2:J" + rowIdx + "),\"\")");

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            // Only I/O faults from workbook construction/serialisation are wrapped;
            // any RuntimeException propagates to the GlobalExceptionHandler unaltered.
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] findingsXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        LocalDate today = LocalDate.now();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Findings");
            CellStyle hdrStyle = headerStyle(wb);
            CellStyle wrapStyle = wrapStyle(wb);
            String[] headers = { "Title", "Severity", "Owner", "Created", "Days Open",
                "Tier", "CAPA Stage", "Status", "Days Open (formula)" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }
            int rowIdx = 1;
            for (AuditFinding f : findings) {
                Row row = sheet.createRow(rowIdx++);
                Cell t = row.createCell(0); t.setCellValue(safe(f.getTitle())); t.setCellStyle(wrapStyle);
                row.createCell(1).setCellValue(safe(f.getSeverity()));
                row.createCell(2).setCellValue(safe(f.getOwner()));
                LocalDateTime created = f.getCreatedAt();
                if (created != null) row.createCell(3).setCellValue(created.toLocalDate().toString());
                long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                row.createCell(4).setCellValue(daysOpen);
                String tier = daysOpen >= 90 ? "EXEC" : daysOpen >= 60 ? "DIRECTOR" : daysOpen >= 30 ? "MANAGER" : "ON_TRACK";
                row.createCell(5).setCellValue(tier);
                row.createCell(6).setCellValue(safe(f.getCapaStage()));
                row.createCell(7).setCellValue(safe(f.getStatus()));
                // Live "Days Open" formula recomputed when the workbook is opened
                Cell daysFormula = row.createCell(8);
                daysFormula.setCellFormula("IF(D" + rowIdx + "<>\"\",TODAY()-DATEVALUE(D" + rowIdx + "),\"\")");
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            // Only I/O faults from workbook construction/serialisation are wrapped;
            // any RuntimeException propagates to the GlobalExceptionHandler unaltered.
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] kriDashboardXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(org);
        // QUAL-BCMP-006: one query for each KRI's latest reading instead of a
        // per-KRI N+1 loop. Key present ⇒ a reading exists; value [0]=band (may
        // be null), [1]=readingValue.
        Map<Long, Object[]> latestByKri = latestReadingByKri(kris);

        try (Workbook wb = new XSSFWorkbook()) {
            // ── Sheet 1: KRIs ─────────────────────────────────
            Sheet sheet = wb.createSheet("KRIs");
            CellStyle hdrStyle = headerStyle(wb);
            String[] headers = { "Code", "KRI", "Direction", "Green ≤", "Amber ≤",
                "Latest Reading", "Latest Band", "Frequency", "Owner", "Framework" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }
            int rowIdx = 1;
            for (KriDefinition k : kris) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(k.getKriCode()));
                row.createCell(1).setCellValue(safe(k.getTitleEn()));
                row.createCell(2).setCellValue(safe(k.getValueDirection()));
                if (k.getGreenThreshold() != null) row.createCell(3).setCellValue(k.getGreenThreshold().doubleValue());
                if (k.getAmberThreshold() != null) row.createCell(4).setCellValue(k.getAmberThreshold().doubleValue());
                Object[] latest = latestByKri.get(k.getId());
                if (latest != null) {
                    row.createCell(5).setCellValue(((BigDecimal) latest[1]).doubleValue());
                    row.createCell(6).setCellValue(safe((String) latest[0]));
                }
                row.createCell(7).setCellValue(safe(k.getFrequency()));
                row.createCell(8).setCellValue(k.getOwnerUserId() != null ? String.valueOf(k.getOwnerUserId()) : "—");
                row.createCell(9).setCellValue(safe(k.getClauseRef()));
            }
            // ── Sheet 2: Summary with formulas ───────────────
            Sheet summary = wb.createSheet("Summary");
            summary.createRow(0).createCell(0).setCellValue("Band");
            summary.getRow(0).createCell(1).setCellValue("Count");
            summary.getRow(0).getCell(0).setCellStyle(hdrStyle);
            summary.getRow(0).getCell(1).setCellStyle(hdrStyle);
            String[] bands = { "GREEN", "AMBER", "RED" };
            for (int i = 0; i < bands.length; i++) {
                Row r = summary.createRow(i + 1);
                r.createCell(0).setCellValue(bands[i]);
                Cell c = r.createCell(1);
                c.setCellFormula("COUNTIF(KRIs!G2:G" + rowIdx + ",\"" + bands[i] + "\")");
            }
            Row totalRow = summary.createRow(bands.length + 2);
            totalRow.createCell(0).setCellValue("Total");
            totalRow.getCell(0).setCellStyle(hdrStyle);
            Cell totalCell = totalRow.createCell(1);
            totalCell.setCellFormula("SUM(B2:B" + (bands.length + 1) + ")");
            totalCell.setCellStyle(hdrStyle);

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            summary.autoSizeColumn(0); summary.autoSizeColumn(1);
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            // Only I/O faults from workbook construction/serialisation are wrapped;
            // any RuntimeException propagates to the GlobalExceptionHandler unaltered.
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(font);
        s.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }
    private CellStyle wrapStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }
    private byte[] writeWorkbook(Workbook wb) throws java.io.IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
