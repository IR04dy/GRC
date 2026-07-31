package com.tss.grc.module.reporting.service;

import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.module.reporting.dto.BoardPackAggregate;
import com.tss.grc.module.reporting.dto.BoardPackAggregate.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * S1 — One-click board reporting pack generator (PDF).
 *
 * Produces a multi-section PDF combining:
 *  1. Cover page (organisation + period + generated-on)
 *  2. Executive summary (KPIs from every domain)
 *  3. Risk Register — top 20 by inherent score
 *  4. Findings aging snapshot (escalation tier breakdown)
 *  5. KRI dashboard (band counts + KRI table)
 *  6. Compliance framework coverage (per framework: policies/findings/controls)
 *  7. Change management snapshot (by status + by type)
 *  8. Ethics &amp; Compliance highlights (cases by category + AML STR count)
 *  9. Sustainability &amp; ESG snapshot
 * 10. Closing notes
 *
 * Bilingual: pass lang="ar" to flip to RTL throughout.
 *
 * <p><b>D-5 — this class renders; it no longer computes.</b> Every figure comes from
 * {@link BoardPackAggregateService#build(Scope)}, which {@link BoardPackPptxService} and
 * {@code GET /api/v1/reporting/board-pack.json} also call. It previously held 35 repository call
 * sites and the deck held its own 32, so the PDF and the deck of one board pack were two
 * implementations of one document; {@link BoardPackAggregate} records what they had already drifted
 * on. The only remaining decisions here are presentation ones — which language to show, how to
 * render an absent value, how many rows fit on a page.
 */
@Service
@RequiredArgsConstructor
public class BoardPackService {

    /** What an absent value looks like in a rendered pack. The aggregate carries null. */
    private static final String NONE = "—";

    private final BoardPackAggregateService aggregateService;

    @Transactional(readOnly = true)
    public byte[] generatePdf(String lang) {
        BoardPackAggregate pack = aggregateService.build(Scope.FULL);
        boolean ar = "ar".equalsIgnoreCase(lang);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            // Shared executive footer — hairline + classification + Page X of Y.
            // Board packs sit at the highest sensitivity in the GRC platform
            // (combine financial KRIs + open findings + ethics cases), so they
            // ship as Confidential.
            writer.setPageEvent(PdfTheme.professionalFooter(
                ar ? "حزمة المجلس — TSS GRC" : "Board Pack — TSS GRC",
                "Confidential"));
            doc.open();

            // 1. Cover
            renderCover(doc, pack.header(), ar);

            // 2. Executive summary
            doc.newPage();
            renderSectionHeader(doc, ar ? "ملخص تنفيذي" : "Executive Summary", ar);
            renderExecutiveSummary(doc, pack.executive(), ar);

            // 3. Risk Register
            doc.newPage();
            renderSectionHeader(doc, ar ? "سجل المخاطر — أعلى ٢٠" : "Risk Register — Top 20", ar);
            renderRiskRegister(doc, pack.topRisks(), ar);

            // 4. Findings aging
            doc.newPage();
            renderSectionHeader(doc, ar ? "ملاحظات التدقيق وتقادمها" : "Audit Findings — Aging Snapshot", ar);
            renderFindings(doc, pack.aging(), pack.oldestOpenFindings(), ar);

            // 5. KRI dashboard
            doc.newPage();
            renderSectionHeader(doc, ar ? "لوحة مؤشرات المخاطر الرئيسية" : "Key Risk Indicators", ar);
            renderKris(doc, pack.kris(), ar);

            // 6. Framework coverage
            doc.newPage();
            renderSectionHeader(doc, ar ? "تغطية الأطر التنظيمية" : "Regulatory Framework Coverage", ar);
            renderFrameworks(doc, pack.frameworkCoverage(), ar);

            // 7. Change management
            doc.newPage();
            renderSectionHeader(doc, ar ? "إدارة التغيير" : "Change Management", ar);
            renderChanges(doc, pack.changes(), ar);

            // 8. Ethics
            doc.newPage();
            renderSectionHeader(doc, ar ? "الأخلاقيات والامتثال" : "Ethics & Compliance", ar);
            renderEthics(doc, pack.ethics(), ar);

            // 9. Sustainability & ESG (S-N)
            doc.newPage();
            renderSectionHeader(doc, ar ? "الاستدامة والحوكمة البيئية والاجتماعية" : "Sustainability & ESG", ar);
            renderSustainability(doc, pack.sustainability(), ar);

            // 10. Closing
            doc.newPage();
            renderClosing(doc, ar);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Board pack PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Section: Cover ─────────────────────────────────────────────────────
    private void renderCover(Document doc, BoardPackAggregate.Header h, boolean ar) throws DocumentException {
        // Shared executive cover hero — brand band + eyebrow + bilingual
        // organisation name as the title + tagline + Hijri+Gregorian stamp
        // + Confidential badge. Identical look across BCP/Crisis/NDI/board
        // pack so the platform's printed output reads as one document family.
        PdfTheme.Cover cv = new PdfTheme.Cover();
        cv.eyebrow    = "Quarterly Board Pack";
        cv.eyebrowAr  = "حزمة الإدارة الفصلية";
        cv.titleEn    = h.organisationNameEn() == null ? NONE : h.organisationNameEn();
        cv.titleAr    = h.organisationNameAr();
        cv.subtitle   = "Risk · Compliance · Continuity Snapshot";
        cv.subtitleAr = "نظرة شاملة على المخاطر والامتثال والاستمرارية";
        cv.classification = "Confidential";
        cv.ar = ar;   // Arabic covers lay out RTL + right-aligned
        PdfTheme.coverHero(doc, cv);

        // Centered + shaped — a plain centered Paragraph leaves the Arabic
        // disconnected (no bidi shaping outside run-direction-aware elements).
        PdfTheme.centeredLine(doc,
            ar ? "تم إنشاء هذه الحزمة تلقائيًا بواسطة منصة TSS GRC."
               : "This pack was generated automatically by the TSS GRC Platform.",
            PdfFonts.small(9), 180, 0);
    }

    // ── Section: Executive Summary ──────────────────────────────────────────
    private void renderExecutiveSummary(Document doc, BoardPackAggregate.Executive e, boolean ar)
            throws DocumentException {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        kpi(t, ar ? "المخاطر" : "Risks",         String.valueOf(e.totalRisks()),    ar ? e.openRisks() + " مفتوحة"  : e.openRisks() + " open", ar);
        kpi(t, ar ? "حرجة"   : "Critical",       String.valueOf(e.criticalRisks()), ar ? "تتطلب اهتمام"        : "Need attention",      ar);
        kpi(t, ar ? "ملاحظات مفتوحة" : "Open Findings", String.valueOf(e.openFindings()),  ar ? "من إجمالي " + e.totalFindings() : "of " + e.totalFindings() + " total", ar);
        kpi(t, ar ? "ضوابط"  : "Controls",       String.valueOf(e.totalControls()), NONE, ar);
        kpi(t, ar ? "مؤشرات المخاطر" : "KRIs",  String.valueOf(e.totalKris()),     NONE, ar);
        kpi(t, ar ? "قضايا أخلاقيات" : "Ethics Cases", String.valueOf(e.totalEthicsCases()), NONE, ar);
        kpi(t, ar ? "تقارير STR" : "STRs Filed",   String.valueOf(e.strsFiled()), NONE, ar);
        kpi(t, ar ? "تنبيهات AML" : "AML Alerts", String.valueOf(e.amlAlerts()),   NONE, ar);

        doc.add(t);
    }

    /** RTL-aware KPI tile. Can't quite share {@link PdfTheme#kpiTile} verbatim
     *  because the board pack flips paragraph alignment per cell when ar=true;
     *  PdfTheme.kpiTile is LTR-only since it serves the per-document exporters
     *  where Arabic stays inline as a second line. */
    private void kpi(PdfPTable t, String label, String value, String sub, boolean ar) {
        PdfPCell c = new PdfPCell();
        c.setPadding(10);
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setBackgroundColor(PdfFonts.PAPER_BG);
        c.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        // ALIGN semantics mirror under RTL runs — visualStart returns the
        // constant that lands at the reading start (visual right for Arabic).
        int align = PdfTheme.visualStart(ar,
            ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);

        Paragraph l = new Paragraph(label, PdfFonts.small(9));
        l.setAlignment(align);
        c.addElement(l);

        Paragraph v = new Paragraph(value, PdfFonts.kpiValue(20));
        v.setAlignment(align);
        c.addElement(v);

        Paragraph s = new Paragraph(sub, PdfFonts.small(8));
        s.setAlignment(align);
        c.addElement(s);

        t.addCell(c);
    }

    // ── Section: Risk Register top 20 ──────────────────────────────────────
    private void renderRiskRegister(Document doc, List<BoardPackAggregate.RiskRow> risks, boolean ar)
            throws DocumentException {
        float[] widths = new float[]{ 1f, 4.5f, 1f, 1f, 1.5f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "رقم", "المخاطرة", "النتيجة", "المتبقية", "الحالة" }
            : new String[]{ "ID",  "Risk",     "Score",   "Residual", "Status" };
        for (String h : hdr) t.addCell(headerCell(h, ar));
        for (BoardPackAggregate.RiskRow r : risks) {
            t.addCell(bodyCell(r.reference(), false));
            t.addCell(bodyCell(safe(r.title(ar)), ar));
            // A null inherent score renders as 0, not as an em-dash: the column is the ranking key,
            // and SQL Server sorts NULLs last under DESC, which is where a 0 belongs anyway.
            t.addCell(bodyCell(String.valueOf(r.inherentScore() == null ? 0 : r.inherentScore()), false));
            t.addCell(bodyCell(r.residualScore() == null ? NONE : String.valueOf(r.residualScore()), false));
            t.addCell(bodyCell(safe(r.status()), false));
        }
        doc.add(t);
    }

    // ── Section: Findings ───────────────────────────────────────────────────
    private void renderFindings(Document doc, BoardPackAggregate.Aging aging,
                                List<BoardPackAggregate.FindingRow> findings, boolean ar)
            throws DocumentException {
        // Shaping-aware line — plain paragraphs never shape Arabic.
        PdfTheme.line(doc,
            ar ? "تقادم الملاحظات: في المسار " + aging.onTrack() + " · مدير " + aging.manager()
                 + " · مدير عام " + aging.director() + " · تنفيذي " + aging.executive()
               : "Aging tiers: On Track " + aging.onTrack() + " · Manager " + aging.manager()
                 + " · Director " + aging.director() + " · Exec " + aging.executive(),
            PdfFonts.normal(11), ar, 0, 10);

        float[] widths = new float[]{ 4f, 1.3f, 1.5f, 1f, 1.3f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "الملاحظة", "الأهمية", "المالك", "الأيام", "الحالة" }
            : new String[]{ "Finding",  "Severity", "Owner",  "Days",   "Status" };
        for (String h : hdr) t.addCell(headerCell(h, ar));
        for (BoardPackAggregate.FindingRow f : findings) {
            t.addCell(bodyCell(safe(f.title()), ar));
            t.addCell(bodyCell(safe(f.severity()), false));
            t.addCell(bodyCell(safe(f.owner()), ar));
            t.addCell(bodyCell(String.valueOf(f.daysOpen() == null ? 0 : f.daysOpen()), false));
            t.addCell(bodyCell(safe(f.status()), false));
        }
        doc.add(t);
    }

    // ── Section: KRIs ───────────────────────────────────────────────────────
    private void renderKris(Document doc, BoardPackAggregate.Kris kris, boolean ar) throws DocumentException {
        PdfTheme.line(doc,
            ar ? "نطاقات: أخضر " + kris.green() + " · أصفر " + kris.amber() + " · أحمر " + kris.red()
                 + " · بدون بيانات " + kris.noData()
               : "Bands: Green " + kris.green() + " · Amber " + kris.amber() + " · Red " + kris.red()
                 + " · No Data " + kris.noData(),
            PdfFonts.normal(11), ar, 0, 10);

        float[] widths = new float[]{ 1.2f, 4f, 1f, 1.2f, 1.2f, 1.4f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "الرمز", "اسم المؤشر", "نطاق", "آخر قراءة", "أخضر ≤", "أصفر ≤" }
            : new String[]{ "Code", "KRI",        "Band",  "Latest",     "Green ≤", "Amber ≤" };
        for (String h : hdr) t.addCell(headerCell(h, ar));
        for (BoardPackAggregate.KriRow k : kris.rows()) {
            t.addCell(bodyCell(safe(k.code()), false));
            t.addCell(bodyCell(safe(k.title(ar)), ar));
            t.addCell(bodyCell(safe(k.band()), false));
            t.addCell(bodyCell(plain(k.latestValue()), false));
            t.addCell(bodyCell(plain(k.greenThreshold()), false));
            t.addCell(bodyCell(plain(k.amberThreshold()), false));
        }
        doc.add(t);
    }

    // ── Section: Framework coverage ─────────────────────────────────────────
    private void renderFrameworks(Document doc, List<BoardPackAggregate.FrameworkRow> frameworks, boolean ar)
            throws DocumentException {
        float[] widths = new float[]{ 1.3f, 4f, 1f, 1f, 1.3f, 1.3f };
        PdfPTable t = buildTable(widths, ar);
        String[] hdr = ar
            ? new String[]{ "الرمز", "الإطار", "ضوابط", "سياسات", "تغطية كاملة", "ملاحظات مفتوحة" }
            : new String[]{ "Code", "Framework","Ctrls", "Polcs",  "Full %",      "Open Findings" };
        for (String h : hdr) t.addCell(headerCell(h, ar));

        for (BoardPackAggregate.FrameworkRow f : frameworks) {
            t.addCell(bodyCell(safe(f.code()), false));
            t.addCell(bodyCell(f.name(ar), ar));
            t.addCell(bodyCell(String.valueOf(f.controls()), false));
            t.addCell(bodyCell(String.valueOf(f.policies()), false));
            t.addCell(bodyCell(f.fullPct() == null ? NONE : f.fullPct() + "%", false));
            t.addCell(bodyCell(String.valueOf(f.openFindings()), false));
        }
        doc.add(t);

        // Small-print attribution (shaping-aware). The omission happens in the aggregate — a
        // framework with no footprint tells a board nothing — so every consumer sees the same list.
        PdfTheme.line(doc,
            ar ? "* لا تظهر الأطر الخالية تمامًا من السياسات والضوابط والملاحظات."
               : "* Frameworks with zero policies, controls and findings are omitted.",
            PdfFonts.small(8), ar, 8, 0);
    }

    // ── Section: Change Mgmt ────────────────────────────────────────────────
    private void renderChanges(Document doc, BoardPackAggregate.Changes c, boolean ar) throws DocumentException {
        PdfTheme.line(doc,
            ar
              ? "إجمالي " + c.total() + " · قيد التنفيذ " + c.inFlight() + " · ناجحة " + c.success()
                + " · تراجع " + c.rolledBack() + " · طارئة " + c.emergency()
              : "Total " + c.total() + " · In-flight " + c.inFlight() + " · Success " + c.success()
                + " · Rolled-back " + c.rolledBack() + " · Emergency " + c.emergency(),
            PdfFonts.normal(11), ar, 0, 0);
    }

    // ── Section: Ethics & Compliance ───────────────────────────────────────
    private void renderEthics(Document doc, BoardPackAggregate.Ethics e, boolean ar) throws DocumentException {
        PdfTheme.line(doc,
            ar
              ? "قضايا: " + e.totalCases() + " · مفتوحة " + e.open() + " · مُثبتة " + e.substantiated()
                + " · رشاوى/عمولات " + e.briberyOrKickback() + " · غسل أموال " + e.amlSuspicion()
                + " · انتقام " + e.retaliation() + " · STR مُرفعة " + e.strsFiled()
              : "Cases: " + e.totalCases() + " · Open " + e.open() + " · Substantiated " + e.substantiated()
                + " · Bribery/Kickback " + e.briberyOrKickback() + " · AML suspicion " + e.amlSuspicion()
                + " · Retaliation " + e.retaliation() + " · STRs filed " + e.strsFiled(),
            PdfFonts.normal(11), ar, 0, 0);
    }

    // ── Section: Sustainability & ESG (S-N) ────────────────────────────────
    /**
     * ESG executive snapshot (overall readiness score + band, GHG inventory, CMA/TCFD disclosure
     * readiness, SGI on-track, open corrective actions, top gaps). The defensive wrapping now lives
     * in the aggregate builder, which reports {@code available=false} if the sustainability
     * subsystem did not answer — so a board sees em-dashes rather than a page of confident zeros.
     */
    private void renderSustainability(Document doc, BoardPackAggregate.Sustainability s, boolean ar)
            throws DocumentException {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        kpi(t, ar ? "درجة ESG"        : "ESG Score",         num(s.esgScore()),               safe(s.esgBand()),                          ar);
        kpi(t, ar ? "إجمالي الانبعاثات" : "GHG Total (tCO2e)", num(s.ghgTotal()),               ar ? "النطاقات 1-3" : "Scopes 1-3",         ar);
        kpi(t, ar ? "إفصاح CMA"        : "CMA Readiness",     num(s.cmaReadinessPct()) + "%",  ar ? "المواد 91-94" : "Art. 91-94",         ar);
        kpi(t, ar ? "توافق TCFD"       : "TCFD Alignment",    num(s.tcfdAlignmentPct()) + "%", NONE,                                       ar);
        kpi(t, ar ? "المبادرة الخضراء"  : "SGI On-Track",      s.sgiOnTrack() + " / " + s.sgiTotal(), ar ? "التزامات نشطة" : "Active commitments", ar);
        kpi(t, ar ? "إجراءات مفتوحة"    : "Open CAPAs",        num(s.openCapas()),              NONE,                                       ar);
        kpi(t, ar ? "أعلى الفجوات"      : "Top ESG Gaps",      String.valueOf(s.topGapCount()), NONE,                                       ar);
        kpi(t, ar ? "النطاق 3"          : "Scope 3 (tCO2e)",   num(s.scope3Total()),            NONE,                                       ar);
        doc.add(t);
    }

    private void renderClosing(Document doc, boolean ar) throws DocumentException {
        PdfTheme.centeredLine(doc,
            ar
              ? "تم إعداد هذه الحزمة آليًا من بيانات منصة TSS GRC الحية. "
                + "للاستفسارات حول البيانات الواردة، يرجى التواصل مع مكتب إدارة المخاطر المؤسسية."
              : "This pack is auto-generated from live TSS GRC platform data. "
                + "For questions about any figure, contact the Group ERM Office.",
            PdfFonts.small(10), 40, 0);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private void renderSectionHeader(Document doc, String title, boolean ar) throws DocumentException {
        // Shaping-aware — section titles are Arabic sentences in ar packs; a
        // plain Paragraph rendered them disconnected (and ALIGN_RIGHT would be
        // mirrored inside RTL elements — PdfTheme.line handles both).
        PdfTheme.line(doc, title, PdfFonts.title(16), ar, 0, 12);
    }

    private PdfPTable buildTable(float[] widths, boolean ar) throws DocumentException {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        return t;
    }

    private PdfPCell headerCell(String text, boolean ar) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(10)));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setPadding(6);
        c.setRunDirection(ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        // ALIGN semantics mirror under RTL runs — visualStart lands at the reading start.
        c.setHorizontalAlignment(PdfTheme.visualStart(ar,
            ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR));
        c.setBorderColor(PdfFonts.HAIRLINE);
        return c;
    }

    private PdfPCell bodyCell(String text, boolean rtl) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.body(9)));
        c.setPadding(5);
        c.setRunDirection(rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        c.setHorizontalAlignment(PdfTheme.visualStart(rtl,
            rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        return c;
    }

    private static String safe(String s) { return s == null ? NONE : s; }

    private static String plain(BigDecimal v) { return v == null ? NONE : v.toPlainString(); }

    /** Whole numbers print without a decimal point, matching what the ESG tiles showed before D-5. */
    private static String num(Double v) {
        if (v == null) return NONE;
        return v == Math.floor(v) && !v.isInfinite() ? String.valueOf(v.longValue()) : String.valueOf(v);
    }
}
