package com.tss.grc.module.reporting.service;

import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * S1 — Board pack PowerPoint generator. Sister to {@link BoardPackService}
 * but emits a 16:9 PPTX deck via Apache POI XSLF for executives who want
 * slides instead of a long-form PDF.
 *
 * Slides:
 *  1. Title (org name + period + generated-on)
 *  2. Executive summary KPIs
 *  3. Top risks
 *  4. Findings aging snapshot
 *  5. KRI band summary
 *  6. Change management snapshot
 *  7. Ethics & Compliance highlights
 */
@Service
@RequiredArgsConstructor
public class BoardPackPptxService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Color PRIMARY = new Color(0x09, 0x42, 0x42);
    private static final Color INK     = new Color(0x15, 0x20, 0x1D);
    private static final Color INK_3   = new Color(0x5C, 0x6A, 0x65);
    private static final Color SURFACE = new Color(0xFA, 0xF8, 0xF4);

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriMeasurementRepository measRepo;
    private final ChangeRequestRepository changeRepo;
    private final EthicsCaseRepository caseRepo;
    private final AmlAlertRepository amlRepo;

    @Transactional(readOnly = true)
    public byte[] generatePptx(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new java.awt.Dimension(960, 540)); // 16:9 standard

            // 1. Title
            slideTitle(ppt, o, ar);
            // 2. Exec summary
            slideExecSummary(ppt, org, ar);
            // 3. Top risks
            slideTopRisks(ppt, org, ar);
            // 4. Findings aging
            slideFindings(ppt, org, ar);
            // 5. KRI bands
            slideKris(ppt, org, ar);
            // 6. Change snapshot
            slideChanges(ppt, org, ar);
            // 7. Ethics highlights
            slideEthics(ppt, org, ar);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ppt.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ExportException("PPTX", "Board pack PPTX generation failed", e);
        }
    }

    // ── Slide builders ──────────────────────────────────────────────────────

    private void slideTitle(XMLSlideShow ppt, Organisation o, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        background(s, SURFACE);

        String orgName = o == null ? "—" : (ar && o.getNameAr() != null ? o.getNameAr() : o.getNameEn());
        addText(s, ar ? "حزمة الإدارة الفصلية" : "Quarterly Board Pack",
            new Rectangle(60, 140, 840, 30), 18, false, INK_3, ar);
        addText(s, orgName,
            new Rectangle(60, 180, 840, 80), 44, true, PRIMARY, ar);
        addText(s, ar ? "نظرة شاملة على المخاطر والامتثال والاستمرارية"
                     : "Risk · Compliance · Continuity Snapshot",
            new Rectangle(60, 270, 840, 30), 18, false, INK, ar);
        addText(s, (ar ? "تم الإنشاء: " : "Generated: ") + LocalDateTime.now().format(DT_FMT),
            new Rectangle(60, 470, 840, 24), 11, false, INK_3, ar);
    }

    private void slideExecSummary(XMLSlideShow ppt, Long org, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        slideHeader(s, ar ? "ملخص تنفيذي" : "Executive Summary", ar);

        long totalRisks    = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getTotalElements();
        long criticalRisks = riskRepo.countByOrganisationIdAndRiskLevel(org, "CRITICAL");
        long openFindings  = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent()
            .stream().filter(f -> !"CLOSED".equals(f.getStatus())).count();
        long totalKris     = kriRepo.countByOrganisationIdAndIsDeletedFalse(org);
        long totalCases    = caseRepo.countByOrganisationId(org);
        long strs          = amlRepo.countByOrganisationIdAndStrFiled(org, true);

        // 6 KPI cards in a 3x2 grid
        String[][] kpis = {
            { ar ? "المخاطر"          : "Risks",          String.valueOf(totalRisks) },
            { ar ? "حرجة"              : "Critical",      String.valueOf(criticalRisks) },
            { ar ? "ملاحظات مفتوحة"   : "Open Findings",  String.valueOf(openFindings) },
            { ar ? "مؤشرات المخاطر"  : "KRIs",            String.valueOf(totalKris) },
            { ar ? "قضايا أخلاقيات"  : "Ethics Cases",    String.valueOf(totalCases) },
            { ar ? "تقارير STR"       : "STRs Filed",      String.valueOf(strs) },
        };
        int cardW = 280, cardH = 110, gapX = 20, gapY = 20;
        int startX = 60, startY = 110;
        for (int i = 0; i < kpis.length; i++) {
            int row = i / 3, col = i % 3;
            int x = startX + col * (cardW + gapX);
            int y = startY + row * (cardH + gapY);
            kpiCard(s, kpis[i][0], kpis[i][1], new Rectangle(x, y, cardW, cardH), ar);
        }
    }

    private void slideTopRisks(XMLSlideShow ppt, Long org, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        slideHeader(s, ar ? "أعلى ١٠ مخاطر" : "Top 10 Risks", ar);

        // Page.getContent() is immutable — copy before the in-place sort below.
        List<Risk> risks = new java.util.ArrayList<>(
            riskRepo.findByOrganisationId(org, PageRequest.of(0, 200)).getContent());
        risks.sort(Comparator.comparingInt((Risk r) -> r.getInherentScore() == null ? 0 : r.getInherentScore()).reversed());
        if (risks.size() > 10) risks = risks.subList(0, 10);

        String[][] rows = new String[risks.size() + 1][4];
        rows[0] = ar
            ? new String[]{ "الرقم", "المخاطرة", "النتيجة", "الحالة" }
            : new String[]{ "ID",    "Risk",      "Score",   "Status" };
        for (int i = 0; i < risks.size(); i++) {
            Risk r = risks.get(i);
            String name = ar && r.getTitleAr() != null ? r.getTitleAr() : r.getTitleEn();
            rows[i + 1] = new String[]{
                "R-" + String.format("%05d", r.getId()),
                name,
                String.valueOf(r.getInherentScore() == null ? 0 : r.getInherentScore()),
                safe(r.getStatus()),
            };
        }
        addTable(s, rows, new double[]{ 0.10, 0.65, 0.10, 0.15 }, new Rectangle(60, 110, 840, 380), ar);
    }

    private void slideFindings(XMLSlideShow ppt, Long org, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        slideHeader(s, ar ? "ملاحظات التدقيق" : "Audit Findings", ar);

        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        LocalDate today = LocalDate.now();
        long onTrack = 0, mgr = 0, dir = 0, exec = 0, closed = 0;
        for (AuditFinding f : findings) {
            if ("CLOSED".equals(f.getStatus())) { closed++; continue; }
            LocalDateTime created = f.getCreatedAt();
            long days = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            if (days >= 90) exec++;
            else if (days >= 60) dir++;
            else if (days >= 30) mgr++;
            else onTrack++;
        }
        String[][] rows = new String[][]{
            ar ? new String[]{ "التصعيد", "العدد" } : new String[]{ "Tier", "Count" },
            { ar ? "في المسار" : "On Track",     String.valueOf(onTrack) },
            { ar ? "تصعيد للمدير" : "Manager",  String.valueOf(mgr) },
            { ar ? "تصعيد للمدير العام" : "Director", String.valueOf(dir) },
            { ar ? "تصعيد تنفيذي" : "Executive", String.valueOf(exec) },
            { ar ? "مغلقة" : "Closed",            String.valueOf(closed) },
        };
        addTable(s, rows, new double[]{ 0.7, 0.3 }, new Rectangle(60, 110, 480, 320), ar);

        // Summary callout
        addText(s, ar
                ? "إجمالي ملاحظات مفتوحة: " + (onTrack + mgr + dir + exec)
                : "Total open findings: " + (onTrack + mgr + dir + exec),
            new Rectangle(580, 110, 320, 40), 18, true, PRIMARY, ar);
    }

    private void slideKris(XMLSlideShow ppt, Long org, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        slideHeader(s, ar ? "مؤشرات المخاطر" : "Key Risk Indicators", ar);

        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(org);
        long green = 0, amber = 0, red = 0, noData = 0;
        for (KriDefinition k : kris) {
            String latestBand = measRepo.findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(org, k.getId())
                .map(KriMeasurement::getStatus).orElse(null);
            if (latestBand == null) noData++;
            else if ("GREEN".equals(latestBand)) green++;
            else if ("AMBER".equals(latestBand)) amber++;
            else if ("RED".equals(latestBand)) red++;
        }
        // Bar-style summary across the slide
        int total = (int) (green + amber + red + noData);
        int barTop = 130, barH = 40;
        addText(s, ar ? "نطاقات المؤشرات" : "KRI Band Distribution",
            new Rectangle(60, 100, 840, 28), 14, true, INK, ar);
        if (total > 0) {
            int barW = 840;
            int x = 60;
            int gW = (int) Math.round((double) green  / total * barW);
            int aW = (int) Math.round((double) amber  / total * barW);
            int rW = (int) Math.round((double) red    / total * barW);
            int nW = barW - gW - aW - rW;
            colorBar(s, x,                 barTop, gW, barH, new Color(0x6F, 0xBF, 0x8E));
            colorBar(s, x + gW,            barTop, aW, barH, new Color(0xE5, 0xB5, 0x47));
            colorBar(s, x + gW + aW,       barTop, rW, barH, new Color(0xC0, 0x41, 0x3A));
            colorBar(s, x + gW + aW + rW,  barTop, nW, barH, new Color(0xB8, 0xC0, 0xBB));
        }
        addText(s, ar
                ? "أخضر " + green + " · أصفر " + amber + " · أحمر " + red + " · بدون بيانات " + noData
                : "Green " + green + " · Amber " + amber + " · Red " + red + " · No Data " + noData,
            new Rectangle(60, barTop + barH + 12, 840, 24), 13, false, INK, ar);

        // KRI list (top 10)
        int rowCount = Math.min(kris.size(), 10);
        String[][] rows = new String[rowCount + 1][4];
        rows[0] = ar
            ? new String[]{ "الرمز", "المؤشر", "نطاق", "آخر قراءة" }
            : new String[]{ "Code", "KRI",    "Band",   "Latest" };
        for (int i = 0; i < rowCount; i++) {
            KriDefinition k = kris.get(i);
            KriMeasurement latest = measRepo.findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(org, k.getId()).orElse(null);
            rows[i + 1] = new String[]{
                safe(k.getKriCode()),
                ar && k.getTitleAr() != null ? k.getTitleAr() : k.getTitleEn(),
                latest != null ? safe(latest.getStatus()) : "—",
                latest != null ? latest.getValue().toPlainString() : "—",
            };
        }
        addTable(s, rows, new double[]{ 0.15, 0.55, 0.15, 0.15 }, new Rectangle(60, 240, 840, 250), ar);
    }

    private void slideChanges(XMLSlideShow ppt, Long org, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        slideHeader(s, ar ? "إدارة التغيير" : "Change Management", ar);

        long total      = changeRepo.countByOrganisationId(org);
        long inFlight   = changeRepo.countByOrganisationIdAndStatus(org, "IMPLEMENTING")
                        + changeRepo.countByOrganisationIdAndStatus(org, "SCHEDULED")
                        + changeRepo.countByOrganisationIdAndStatus(org, "CAB_PENDING")
                        + changeRepo.countByOrganisationIdAndStatus(org, "IMPACT_ASSESSED")
                        + changeRepo.countByOrganisationIdAndStatus(org, "EMERGENCY_APPROVED");
        long success    = changeRepo.countByOrganisationIdAndOutcome(org, "SUCCESS");
        long rolledBack = changeRepo.countByOrganisationIdAndOutcome(org, "ROLLED_BACK");
        long emergency  = changeRepo.countByOrganisationIdAndChangeType(org, "EMERGENCY");

        String[][] rows = new String[][]{
            ar ? new String[]{ "المقياس", "القيمة" } : new String[]{ "Metric", "Value" },
            { ar ? "الإجمالي" : "Total",          String.valueOf(total) },
            { ar ? "قيد التنفيذ" : "In-flight",   String.valueOf(inFlight) },
            { ar ? "ناجحة" : "Success",            String.valueOf(success) },
            { ar ? "تراجع" : "Rolled-back",       String.valueOf(rolledBack) },
            { ar ? "طارئة" : "Emergency",         String.valueOf(emergency) },
        };
        addTable(s, rows, new double[]{ 0.65, 0.35 }, new Rectangle(60, 110, 480, 320), ar);
    }

    private void slideEthics(XMLSlideShow ppt, Long org, boolean ar) {
        XSLFSlide s = ppt.createSlide();
        slideHeader(s, ar ? "الأخلاقيات والامتثال" : "Ethics & Compliance", ar);

        long total      = caseRepo.countByOrganisationId(org);
        long openCases  = caseRepo.countByOrganisationIdAndStatus(org, "INTAKE")
                        + caseRepo.countByOrganisationIdAndStatus(org, "TRIAGE")
                        + caseRepo.countByOrganisationIdAndStatus(org, "INVESTIGATION");
        long substantiated = caseRepo.countByOrganisationIdAndStatus(org, "SUBSTANTIATED")
                           + caseRepo.countByOrganisationIdAndStatus(org, "REMEDIATION");
        long bribery       = caseRepo.countByOrganisationIdAndCategory(org, "BRIBERY")
                           + caseRepo.countByOrganisationIdAndCategory(org, "KICKBACK");
        long amlSusp       = caseRepo.countByOrganisationIdAndCategory(org, "AML_SUSPICION");
        long retal         = caseRepo.countByOrganisationIdAndCategory(org, "RETALIATION");
        long strs          = amlRepo.countByOrganisationIdAndStrFiled(org, true);

        String[][] rows = new String[][]{
            ar ? new String[]{ "المقياس", "القيمة" } : new String[]{ "Metric", "Value" },
            { ar ? "إجمالي القضايا" : "Total cases",    String.valueOf(total) },
            { ar ? "مفتوحة" : "Open",                    String.valueOf(openCases) },
            { ar ? "مُثبتة" : "Substantiated",           String.valueOf(substantiated) },
            { ar ? "رشاوى/عمولات" : "Bribery / Kickback", String.valueOf(bribery) },
            { ar ? "اشتباه غسل أموال" : "AML suspicion", String.valueOf(amlSusp) },
            { ar ? "انتقام" : "Retaliation",             String.valueOf(retal) },
            { ar ? "STR مُرفعة" : "STRs filed",          String.valueOf(strs) },
        };
        addTable(s, rows, new double[]{ 0.65, 0.35 }, new Rectangle(60, 110, 480, 380), ar);
    }

    // ── XSLF helpers ────────────────────────────────────────────────────────
    private void background(XSLFSlide s, Color color) {
        XSLFAutoShape rect = s.createAutoShape();
        rect.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
        rect.setAnchor(new Rectangle(0, 0, 960, 540));
        rect.setFillColor(color);
        rect.setLineColor(null);
    }

    private void slideHeader(XSLFSlide s, String title, boolean ar) {
        addText(s, title, new Rectangle(60, 36, 840, 50), 28, true, PRIMARY, ar);
        // accent rule
        XSLFAutoShape rule = s.createAutoShape();
        rule.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
        rule.setAnchor(new Rectangle(60, 92, 80, 4));
        rule.setFillColor(new Color(0xC8, 0x93, 0x2A));
        rule.setLineColor(null);
    }

    private void kpiCard(XSLFSlide s, String label, String value, Rectangle anchor, boolean ar) {
        XSLFAutoShape rect = s.createAutoShape();
        rect.setShapeType(org.apache.poi.sl.usermodel.ShapeType.ROUND_RECT);
        rect.setAnchor(anchor);
        rect.setFillColor(SURFACE);
        rect.setLineColor(new Color(0xE5, 0xDF, 0xD2));
        addText(s, label, new Rectangle(anchor.x + 14, anchor.y + 12, anchor.width - 28, 22), 12, false, INK_3, ar);
        addText(s, value, new Rectangle(anchor.x + 14, anchor.y + 38, anchor.width - 28, 60), 36, true, PRIMARY, ar);
    }

    private void addText(XSLFSlide s, String text, Rectangle anchor, int size, boolean bold, Color color, boolean rtl) {
        XSLFTextBox tb = s.createTextBox();
        tb.setAnchor(anchor);
        tb.clearText();
        XSLFTextParagraph p = tb.addNewTextParagraph();
        p.setTextAlign(rtl ? TextParagraph.TextAlign.RIGHT : TextParagraph.TextAlign.LEFT);
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text);
        r.setFontSize((double) size);
        r.setBold(bold);
        r.setFontColor(color);
    }

    private void colorBar(XSLFSlide s, int x, int y, int w, int h, Color color) {
        if (w <= 0) return;
        XSLFAutoShape rect = s.createAutoShape();
        rect.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
        rect.setAnchor(new Rectangle(x, y, w, h));
        rect.setFillColor(color);
        rect.setLineColor(null);
    }

    private void addTable(XSLFSlide s, String[][] rows, double[] colWidthPct, Rectangle anchor, boolean ar) {
        if (rows.length == 0) return;
        int cols = rows[0].length;
        XSLFTable table = s.createTable();
        table.setAnchor(anchor);
        // Set column widths
        // Note: column widths must be set after rows are created — we'll create rows first.
        for (int r = 0; r < rows.length; r++) {
            XSLFTableRow row = table.addRow();
            row.setHeight(r == 0 ? 28 : 24);
            for (int c = 0; c < cols; c++) {
                XSLFTableCell cell = row.addCell();
                cell.clearText();
                XSLFTextParagraph p = cell.addNewTextParagraph();
                p.setTextAlign(ar ? TextParagraph.TextAlign.RIGHT : TextParagraph.TextAlign.LEFT);  // P2 §21: RTL cells for AR
                XSLFTextRun tr = p.addNewTextRun();
                tr.setText(rows[r][c]);
                tr.setFontSize((double) (r == 0 ? 12 : 11));
                tr.setBold(r == 0);
                tr.setFontColor(r == 0 ? Color.WHITE : INK);
                cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
                if (r == 0) cell.setFillColor(PRIMARY);
                else cell.setFillColor((r % 2 == 1) ? Color.WHITE : new Color(0xF5, 0xF1, 0xE9));
            }
        }
        for (int c = 0; c < cols; c++) {
            table.setColumnWidth(c, anchor.width * colWidthPct[c]);
        }
    }

    private static String safe(String s) { return s == null ? "—" : s; }
}
