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
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.entity.KriDefinition;
import com.tss.grc.module.risk.entity.KriReading;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.repository.KriReadingRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Bilingual PDF export service (Q1) using OpenPDF.
 *
 * Locale handling: pass `lang = "ar"` to flip the page direction to RTL and use
 * Arabic column headers. The PdfFonts helper transparently swaps in an
 * Arabic-capable TTF.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriReadingRepository readingRepo;

    // ── Risk Register ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] riskRegisterPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            doc.open();

            addHeader(doc, ar ? "سجل المخاطر" : "Risk Register", o, ar);

            float[] widths = ar
                ? new float[]{ 1.2f, 4f, 1.6f, 1f, 1f, 1f, 1.5f }
                : new float[]{ 1.2f, 4f, 1.6f, 1f, 1f, 1f, 1.5f };
            PdfPTable t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            String[] hdr = ar
                ? new String[]{ "الرقم", "المخاطرة", "الفئة", "الاحتمال", "الأثر", "النتيجة", "الحالة" }
                : new String[]{ "Code",  "Risk",     "Category", "L",        "I",       "Score",   "Status" };
            for (String h : hdr) t.addCell(headerCell(h, ar));

            for (Risk r : risks) {
                String name = ar && r.getTitleAr() != null ? r.getTitleAr() : r.getTitleEn();
                t.addCell(bodyCell("R-" + String.format("%05d", r.getId()), false));
                t.addCell(bodyCell(safe(name), ar));
                t.addCell(bodyCell("", ar)); // category — could be joined
                t.addCell(bodyCell(String.valueOf(r.getInherentLikelihood()), false));
                t.addCell(bodyCell(String.valueOf(r.getInherentImpact()), false));
                t.addCell(bodyCell(String.valueOf(r.getInherentScore()), false));
                t.addCell(bodyCell(safe(r.getStatus()), ar));
            }
            doc.add(t);

            addFooter(doc, ar);
            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Compliance findings (with aging) ───────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] findingsAgingPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        LocalDate today = LocalDate.now();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            doc.open();
            addHeader(doc, ar ? "ملاحظات التدقيق وتقادمها" : "Audit Findings — Aging Report", o, ar);

            float[] widths = new float[]{ 4f, 1.4f, 1.2f, 1f, 1.4f, 1.6f, 1.4f };
            PdfPTable t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            String[] hdr = ar
                ? new String[]{ "الملاحظة", "الأهمية", "المالك", "الأيام", "التصعيد", "مرحلة CAPA", "الحالة" }
                : new String[]{ "Finding",  "Severity", "Owner",  "Days",   "Tier",     "CAPA Stage",  "Status" };
            for (String h : hdr) t.addCell(headerCell(h, ar));

            for (AuditFinding f : findings) {
                LocalDateTime created = f.getCreatedAt();
                long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                String tier = daysOpen >= 90 ? "EXEC" : daysOpen >= 60 ? "DIRECTOR" : daysOpen >= 30 ? "MANAGER" : "ON_TRACK";
                t.addCell(bodyCell(safe(f.getTitle()), ar));
                t.addCell(bodyCell(safe(f.getSeverity()), false));
                t.addCell(bodyCell(safe(f.getOwner()), ar));
                t.addCell(bodyCell(String.valueOf(daysOpen), false));
                t.addCell(bodyCell(tier, false));
                t.addCell(bodyCell(safe(f.getCapaStage()), false));
                t.addCell(bodyCell(safe(f.getStatus()), false));
            }
            doc.add(t);
            addFooter(doc, ar);
            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── KRI dashboard ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] kriDashboardPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<KriDefinition> kris = kriRepo.findByOrganisationIdOrderByCodeAsc(org, PageRequest.of(0, 200)).getContent();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            doc.open();
            addHeader(doc, ar ? "لوحة مؤشرات المخاطر الرئيسية" : "Key Risk Indicators — Dashboard", o, ar);

            float[] widths = new float[]{ 1.3f, 4f, 1f, 1.3f, 1.3f, 1.3f, 1.6f };
            PdfPTable t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            String[] hdr = ar
                ? new String[]{ "الرمز", "اسم المؤشر", "الحالة", "آخر قراءة", "حد أخضر", "حد أصفر", "المالك" }
                : new String[]{ "Code",  "KRI",        "Band",    "Latest",     "Green ≤", "Amber ≤", "Owner" };
            for (String h : hdr) t.addCell(headerCell(h, ar));

            for (KriDefinition k : kris) {
                KriReading latest = readingRepo.findFirstByKriIdOrderByReadingAtDesc(k.getId()).orElse(null);
                String name = ar && k.getNameAr() != null ? k.getNameAr() : k.getNameEn();
                t.addCell(bodyCell(safe(k.getCode()), false));
                t.addCell(bodyCell(safe(name), ar));
                t.addCell(bodyCell(latest != null ? safe(latest.getBand()) : "—", false));
                t.addCell(bodyCell(latest != null ? latest.getReadingValue().toPlainString() : "—", false));
                t.addCell(bodyCell(k.getGreenMax() != null ? k.getGreenMax().toPlainString() : "—", false));
                t.addCell(bodyCell(k.getAmberMax() != null ? k.getAmberMax().toPlainString() : "—", false));
                t.addCell(bodyCell(safe(k.getOwner()), ar));
            }
            doc.add(t);
            addFooter(doc, ar);
            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Common helpers ─────────────────────────────────────────────────────

    private void addHeader(Document doc, String title, Organisation o, boolean ar) throws DocumentException {
        Paragraph p = new Paragraph(title, PdfFonts.bold(18));
        p.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        doc.add(p);
        if (o != null) {
            String orgName = ar && o.getNameAr() != null ? o.getNameAr() : o.getNameEn();
            Paragraph sub = new Paragraph(orgName + "  ·  " + LocalDate.now().format(DATE_FMT), PdfFonts.small(10));
            sub.setAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            sub.setSpacingAfter(8);
            doc.add(sub);
        }
    }

    private void addFooter(Document doc, boolean ar) throws DocumentException {
        Paragraph footer = new Paragraph(
            ar ? "تم إنشاء هذا التقرير تلقائيًا بواسطة منصة TSS GRC."
               : "This report was generated automatically by the TSS GRC Platform.",
            PdfFonts.small(8));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(12);
        doc.add(footer);
    }

    private PdfPCell headerCell(String text, boolean ar) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(10)));
        c.setBackgroundColor(new Color(0x09, 0x42, 0x42));
        c.setPadding(6);
        c.setHorizontalAlignment(ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        c.setBorderColor(new Color(0xD6, 0xCE, 0xBC));
        return c;
    }

    private PdfPCell bodyCell(String text, boolean rtl) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.normal(9)));
        c.setPadding(5);
        c.setHorizontalAlignment(rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setRunDirection(rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        c.setBorderColor(new Color(0xE5, 0xDF, 0xD2));
        return c;
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
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
        } catch (Exception e) {
            throw new RuntimeException("Excel generation failed", e);
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
        } catch (Exception e) {
            throw new RuntimeException("Excel generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] kriDashboardXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<KriDefinition> kris = kriRepo.findByOrganisationIdOrderByCodeAsc(org, PageRequest.of(0, 200)).getContent();

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
                row.createCell(0).setCellValue(safe(k.getCode()));
                row.createCell(1).setCellValue(safe(k.getNameEn()));
                row.createCell(2).setCellValue(safe(k.getDirection()));
                if (k.getGreenMax() != null) row.createCell(3).setCellValue(k.getGreenMax().doubleValue());
                if (k.getAmberMax() != null) row.createCell(4).setCellValue(k.getAmberMax().doubleValue());
                KriReading latest = readingRepo.findFirstByKriIdOrderByReadingAtDesc(k.getId()).orElse(null);
                if (latest != null) {
                    row.createCell(5).setCellValue(latest.getReadingValue().doubleValue());
                    row.createCell(6).setCellValue(safe(latest.getBand()));
                }
                row.createCell(7).setCellValue(safe(k.getFrequency()));
                row.createCell(8).setCellValue(safe(k.getOwner()));
                row.createCell(9).setCellValue(safe(k.getFramework()));
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
        } catch (Exception e) {
            throw new RuntimeException("Excel generation failed", e);
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

    // Suppress unused warning for CellRangeAddress import (kept for future merging)
    @SuppressWarnings("unused")
    private static final Class<?> KEEP = CellRangeAddress.class;
}
