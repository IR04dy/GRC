package com.tss.grc.module.reporting.service;

import com.tss.grc.common.exception.ExportException;
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
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Bilingual Microsoft Word (.docx) export service using Apache POI XWPF.
 *
 * <p>Produces the same three core operational reports as {@link ExportService}
 * (Risk Register, Findings Aging, KRI Dashboard) but in Word format for
 * teams that want to edit, annotate, or merge into larger reports.
 *
 * <p>Arabic support: pass {@code lang = "ar"} to render Arabic headers and
 * set the document/table run direction to RTL. POI honours the
 * {@code w:bidi} paragraph property so right-aligned Arabic flows correctly.
 */
@Service
@RequiredArgsConstructor
public class DocxExportService {

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriReadingRepository readingRepo;

    // ── Risk Register ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] riskRegisterDocx(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        long critical = risks.stream().filter(r -> "CRITICAL".equalsIgnoreCase(r.getRiskLevel())).count();
        long high     = risks.stream().filter(r -> "HIGH".equalsIgnoreCase(r.getRiskLevel())).count();
        long open     = risks.stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus())).count();

        try (XWPFDocument doc = new XWPFDocument()) {
            applyLandscape(doc);
            cover(doc, o, ar,
                ar ? "سجل المخاطر المؤسسية" : "Enterprise Risk Register",
                ar ? "سجل المخاطر" : "Risk Register");
            kpiStrip(doc, ar, new String[][]{
                { ar ? "إجمالي المخاطر" : "Total risks",  String.valueOf(risks.size()) },
                { ar ? "حرجة" : "Critical",                String.valueOf(critical) },
                { ar ? "عالية" : "High",                   String.valueOf(high) },
                { ar ? "مفتوحة" : "Open",                  String.valueOf(open) },
            });

            String[] hdr = ar
                ? new String[]{ "الرقم", "المخاطرة", "الفئة", "احتمال", "أثر", "نتيجة", "الحالة" }
                : new String[]{ "Code",  "Risk",     "Category", "L",    "I",   "Score", "Status" };
            XWPFTable t = newTable(doc, hdr, ar);

            for (Risk r : risks) {
                String name = ar && r.getTitleAr() != null ? r.getTitleAr() : r.getTitleEn();
                XWPFTableRow row = t.createRow();
                setCell(row, 0, "R-" + String.format("%05d", r.getId()), ar);
                setCell(row, 1, safe(name), ar);
                setCell(row, 2, "—", ar);
                setCell(row, 3, String.valueOf(r.getInherentLikelihood()), ar);
                setCell(row, 4, String.valueOf(r.getInherentImpact()), ar);
                setCell(row, 5, String.valueOf(r.getInherentScore()), ar);
                setCell(row, 6, safe(r.getStatus()), ar);
            }
            return bytes(doc);
        } catch (Exception e) {
            throw new ExportException("Word", "Word generation failed (Risk Register)", e);
        }
    }

    // ── Findings aging ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] findingsAgingDocx(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        LocalDate today = LocalDate.now();
        long open  = findings.stream().filter(f -> !"CLOSED".equalsIgnoreCase(f.getStatus())).count();
        long past30 = 0, past90 = 0;
        for (AuditFinding f : findings) {
            if ("CLOSED".equalsIgnoreCase(f.getStatus())) continue;
            LocalDateTime created = f.getCreatedAt();
            long days = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            if (days >= 90) past90++;
            else if (days >= 30) past30++;
        }

        try (XWPFDocument doc = new XWPFDocument()) {
            applyLandscape(doc);
            cover(doc, o, ar,
                ar ? "تقرير تقادم الملاحظات" : "Findings Aging Report",
                ar ? "ملاحظات التدقيق" : "Audit Findings");
            kpiStrip(doc, ar, new String[][]{
                { ar ? "إجمالي" : "Total",                  String.valueOf(findings.size()) },
                { ar ? "مفتوحة" : "Open",                   String.valueOf(open) },
                { ar ? "متجاوزة 30 يوم" : "Past 30 days",   String.valueOf(past30) },
                { ar ? "متجاوزة 90 يوم" : "Past 90 days",   String.valueOf(past90) },
            });

            String[] hdr = ar
                ? new String[]{ "الملاحظة", "الأهمية", "المالك", "الأيام", "التصعيد", "مرحلة CAPA", "الحالة" }
                : new String[]{ "Finding",  "Severity", "Owner",  "Days",   "Tier",     "CAPA Stage",  "Status" };
            XWPFTable t = newTable(doc, hdr, ar);

            for (AuditFinding f : findings) {
                LocalDateTime created = f.getCreatedAt();
                long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                String tier = daysOpen >= 90 ? "EXEC" : daysOpen >= 60 ? "DIRECTOR"
                            : daysOpen >= 30 ? "MANAGER" : "ON_TRACK";
                XWPFTableRow row = t.createRow();
                setCell(row, 0, safe(f.getTitle()), ar);
                setCell(row, 1, safe(f.getSeverity()), ar);
                setCell(row, 2, safe(f.getOwner()), ar);
                setCell(row, 3, String.valueOf(daysOpen), ar);
                setCell(row, 4, tier, ar);
                setCell(row, 5, safe(f.getCapaStage()), ar);
                setCell(row, 6, safe(f.getStatus()), ar);
            }
            return bytes(doc);
        } catch (Exception e) {
            throw new ExportException("Word", "Word generation failed (Findings)", e);
        }
    }

    // ── KRI dashboard ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] kriDashboardDocx(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<KriDefinition> kris = kriRepo.findByOrganisationIdOrderByCodeAsc(org, PageRequest.of(0, 200)).getContent();
        long green = 0, amber = 0, red = 0, noData = 0;
        for (KriDefinition k : kris) {
            String band = readingRepo.findFirstByKriIdOrderByReadingAtDesc(k.getId())
                .map(KriReading::getBand).orElse(null);
            if (band == null) noData++;
            else if ("GREEN".equalsIgnoreCase(band)) green++;
            else if ("AMBER".equalsIgnoreCase(band)) amber++;
            else if ("RED".equalsIgnoreCase(band)) red++;
        }

        try (XWPFDocument doc = new XWPFDocument()) {
            applyLandscape(doc);
            cover(doc, o, ar,
                ar ? "لوحة مؤشرات المخاطر" : "KRI Dashboard",
                ar ? "مؤشرات المخاطر الرئيسية" : "Key Risk Indicators");
            kpiStrip(doc, ar, new String[][]{
                { ar ? "أخضر" : "Green",            String.valueOf(green) },
                { ar ? "أصفر" : "Amber",            String.valueOf(amber) },
                { ar ? "أحمر" : "Red",              String.valueOf(red) },
                { ar ? "بدون بيانات" : "No data",  String.valueOf(noData) },
            });

            String[] hdr = ar
                ? new String[]{ "الرمز", "اسم المؤشر", "الحالة", "آخر قراءة", "حد أخضر", "حد أصفر", "المالك" }
                : new String[]{ "Code",  "KRI",        "Band",    "Latest",     "Green ≤", "Amber ≤", "Owner" };
            XWPFTable t = newTable(doc, hdr, ar);

            for (KriDefinition k : kris) {
                KriReading latest = readingRepo.findFirstByKriIdOrderByReadingAtDesc(k.getId()).orElse(null);
                String name = ar && k.getNameAr() != null ? k.getNameAr() : k.getNameEn();
                XWPFTableRow row = t.createRow();
                setCell(row, 0, safe(k.getCode()), ar);
                setCell(row, 1, safe(name), ar);
                setCell(row, 2, latest != null ? safe(latest.getBand()) : "—", ar);
                setCell(row, 3, latest != null ? latest.getReadingValue().toPlainString() : "—", ar);
                setCell(row, 4, k.getGreenMax() != null ? k.getGreenMax().toPlainString() : "—", ar);
                setCell(row, 5, k.getAmberMax() != null ? k.getAmberMax().toPlainString() : "—", ar);
                setCell(row, 6, safe(k.getOwner()), ar);
            }
            return bytes(doc);
        } catch (Exception e) {
            throw new ExportException("Word", "Word generation failed (KRI)", e);
        }
    }

    // ── Shared building blocks ─────────────────────────────────────────────

    private static void cover(XWPFDocument doc, Organisation o, boolean ar,
                              String title, String eyebrow) {
        XWPFParagraph eb = doc.createParagraph();
        applyBidi(eb, ar);
        eb.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        XWPFRun ebr = eb.createRun();
        ebr.setText(eyebrow);
        ebr.setColor("C9A977");
        ebr.setBold(true);
        ebr.setFontSize(11);

        XWPFParagraph tp = doc.createParagraph();
        applyBidi(tp, ar);
        tp.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        XWPFRun tr = tp.createRun();
        tr.setText(title);
        tr.setColor("094242");
        tr.setBold(true);
        tr.setFontSize(26);

        if (o != null) {
            XWPFParagraph op = doc.createParagraph();
            applyBidi(op, ar);
            op.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            XWPFRun or = op.createRun();
            or.setText(ar && o.getNameAr() != null ? o.getNameAr() : safe(o.getNameEn()));
            or.setColor("5C6A65");
            or.setFontSize(12);
        }

        XWPFParagraph dp = doc.createParagraph();
        applyBidi(dp, ar);
        dp.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        XWPFRun dr = dp.createRun();
        dr.setText((ar ? "تاريخ الإصدار: " : "Issued: ") + LocalDate.now()
                + (ar ? "  •  تصنيف: داخلي" : "  •  Classification: Internal"));
        dr.setColor("5C6A65");
        dr.setFontSize(9);
        dr.addBreak();
    }

    private static void kpiStrip(XWPFDocument doc, boolean ar, String[][] kpis) {
        XWPFTable strip = doc.createTable(1, kpis.length);
        strip.setWidth("100%");
        strip.removeBorders();
        XWPFTableRow row = strip.getRow(0);
        for (int i = 0; i < kpis.length; i++) {
            XWPFTableCell c = row.getCell(i);
            c.setColor("FAF8F4");
            c.removeParagraph(0);
            XWPFParagraph lbl = c.addParagraph();
            applyBidi(lbl, ar);
            lbl.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            XWPFRun lblr = lbl.createRun();
            lblr.setText(kpis[i][0]);
            lblr.setColor("5C6A65");
            lblr.setFontSize(9);
            lblr.setBold(true);

            XWPFParagraph val = c.addParagraph();
            applyBidi(val, ar);
            val.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            XWPFRun valr = val.createRun();
            valr.setText(kpis[i][1]);
            valr.setColor("094242");
            valr.setFontSize(20);
            valr.setBold(true);
        }
        XWPFParagraph spacer = doc.createParagraph();
        spacer.createRun().addBreak();
    }

    private static XWPFTable newTable(XWPFDocument doc, String[] headers, boolean ar) {
        XWPFTable t = doc.createTable(1, headers.length);
        t.setWidth("100%");
        XWPFTableRow hr = t.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell c = hr.getCell(i);
            c.setColor("094242");
            c.removeParagraph(0);
            XWPFParagraph p = c.addParagraph();
            applyBidi(p, ar);
            p.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            XWPFRun r = p.createRun();
            r.setText(headers[i]);
            r.setColor("FFFFFF");
            r.setBold(true);
            r.setFontSize(10);
        }
        return t;
    }

    private static void setCell(XWPFTableRow row, int col, String text, boolean ar) {
        XWPFTableCell c = row.getCell(col);
        c.removeParagraph(0);
        XWPFParagraph p = c.addParagraph();
        applyBidi(p, ar);
        p.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setColor("1A1A1A");
        r.setFontSize(9);
    }

    /** Toggle RTL on a paragraph by setting the {@code w:bidi} run property. */
    private static void applyBidi(XWPFParagraph p, boolean rtl) {
        if (!rtl) return;
        if (p.getCTP().getPPr() == null) p.getCTP().addNewPPr();
        if (p.getCTP().getPPr().getBidi() == null) p.getCTP().getPPr().addNewBidi();
    }

    /** A4 landscape — register dumps benefit from the extra horizontal room. */
    private static void applyLandscape(XWPFDocument doc) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        if (sect.getPgSz() == null) sect.addNewPgSz();
        sect.getPgSz().setW(BigInteger.valueOf(16838)); // A4 long edge in twentieths of a point
        sect.getPgSz().setH(BigInteger.valueOf(11906)); // short edge
        sect.getPgSz().setOrient(STPageOrientation.LANDSCAPE);
    }

    private static byte[] bytes(XWPFDocument doc) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ExportException("Word", "Word write failed", e);
        }
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "—" : s; }
}
