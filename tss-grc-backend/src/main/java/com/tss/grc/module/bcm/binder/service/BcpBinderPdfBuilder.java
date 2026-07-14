package com.tss.grc.module.bcm.binder.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.module.bcm.binder.entity.BcpBinderExport;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slice 10-F — Offline BCP/DRP binder PDF generation engine.
 *
 * <p>Renders an entire BCP plan as a regulator-grade, off-line-printable
 * PDF binder with the 9 ISO 22301 Annex A sections, bilingual EN/AR
 * with Saudi-native phrasing, a watermark on every page, a controlled-
 * distribution badge on the cover, and footer pagination using Eastern
 * Arabic-Indic numerals ("صفحة ٣ من ١٢") when the binder language is
 * AR or BILINGUAL.
 *
 * <h3>Section order (ISO 22301 Annex A, Saudi-native AR phrasing)</h3>
 * <ol>
 *   <li>Organisational context — السياق التنظيمي</li>
 *   <li>Purpose &amp; scope — الهدف والنطاق</li>
 *   <li>Roles &amp; responsibilities — الأدوار والمسؤوليات</li>
 *   <li>BIA summary — تحليل الأثر التجاري</li>
 *   <li>Continuity strategy — استراتيجية الاستمرارية</li>
 *   <li>Recovery procedures — إجراءات الاسترداد</li>
 *   <li>Contact tree — شجرة الاتصال</li>
 *   <li>Crisis comms templates — قوالب الاتصالات في حالة الأزمات</li>
 *   <li>Appendices — ملاحق</li>
 * </ol>
 *
 * <h3>Language column behaviour</h3>
 * <ul>
 *   <li>{@code EN}        — sequential pages, EN bodies only.</li>
 *   <li>{@code AR}        — sequential pages, AR bodies only, Eastern-numeral footer.</li>
 *   <li>{@code BILINGUAL} — side-by-side 2-column layout (EN | AR) for every section
 *       body. Decided over half-stitched-document because field operators
 *       on paper benefit from being able to scan EN + AR on the same physical
 *       sheet rather than flipping halves.</li>
 * </ul>
 *
 * <p>OpenPDF font registration uses the shared {@link PdfFonts} loader —
 * Open Issue #003 resolved: Noto Kufi Arabic (or Arabic-capable fallback)
 * is loaded from {@code src/main/resources/fonts/} at startup and logged
 * for ops verification.
 */
@Slf4j
@Component
public class BcpBinderPdfBuilder {

    private static final DateTimeFormatter DT_GREG_LONG =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DT_AR_LONG =
        DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ar", "SA"));
    private static final DateTimeFormatter DT_HIJRI =
        DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ar", "SA"))
            .withChronology(java.time.chrono.HijrahChronology.INSTANCE);
    private static final DateTimeFormatter DT_FULL =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Saudi-native ISO 22301 Annex A section catalogue. */
    public static final List<SectionDef> SECTION_CATALOGUE = List.of(
        new SectionDef("context",      "Organisational context",        "السياق التنظيمي"),
        new SectionDef("scope",        "Purpose & scope",                "الهدف والنطاق"),
        new SectionDef("roles",        "Roles & responsibilities",       "الأدوار والمسؤوليات"),
        new SectionDef("bia",          "Business Impact Analysis",        "تحليل الأثر التجاري"),
        new SectionDef("strategy",     "Continuity strategy",            "استراتيجية الاستمرارية"),
        new SectionDef("recovery",     "Recovery procedures",            "إجراءات الاسترداد"),
        new SectionDef("contacts",     "Contact tree",                   "شجرة الاتصال"),
        new SectionDef("comms",        "Crisis communication templates", "قوالب الاتصالات في حالة الأزمات"),
        new SectionDef("appendices",   "Appendices",                     "ملاحق")
    );

    /** Section catalogue row. */
    public record SectionDef(String key, String titleEn, String titleAr) {}

    /** Build-time inputs the PDF engine needs. */
    public record BuildInput(
        BcpBinderExport binder,
        BcpPlan plan,
        Organisation organisation,
        List<BcpSection> sections,
        List<BcpVersion> versions,
        List<BcpPlanAcknowledgement> contacts
    ) {}

    /** Build-time outputs the caller persists back to the binder row. */
    public record BuildResult(byte[] bytes, int pageCount) {}

    /**
     * Generate the binder PDF. Returns the byte payload and the
     * page count so the caller can record both on the binder row.
     */
    public BuildResult build(BuildInput in) {
        BcpBinderExport b = in.binder();
        BcpPlan p = in.plan();
        boolean wantsAr  = !"EN".equalsIgnoreCase(b.getLanguage());
        boolean wantsEn  = !"AR".equalsIgnoreCase(b.getLanguage());
        boolean bilingual = wantsAr && wantsEn;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 72, 60);
        int pageCount;
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            // P3 RTL: a pure-AR binder renders right-to-left (paragraphs + tables).
            // BILINGUAL stays LTR so the side-by-side EN | AR columns keep their reading
            // order; EN is unchanged. Each single-language table below sets the same rd.
            writer.setRunDirection(PdfTheme.runDir(wantsAr && !bilingual));

            String footerDocLabel = (p.getPlanCode() == null ? "BCP" : p.getPlanCode())
                + " · " + nz(b.getVersionLabel(), "v1");
            // Watermark + Eastern-numeral footer in a single page-event helper.
            // Watermark: gray, rotated, every page. Footer: hairline + label +
            // "صفحة X من Y" (Eastern numerals when AR) or "Page X of Y" (EN).
            writer.setPageEvent(new BinderPageEvent(b.getWatermarkText(),
                footerDocLabel, wantsAr));

            // Document metadata — OpenPDF's Document supports addTitle/Author/
            // Subject/Keywords. The PDF /Lang attribute would help screen
            // readers but isn't exposed on the high-level Document class in
            // OpenPDF 1.3.x — written into Keywords as a workaround so the
            // language preference is at least discoverable in the PDF metadata.
            doc.addTitle(nz(p.getTitleEn(), "BCP Binder"));
            doc.addSubject("Business Continuity Plan binder · " + nz(b.getExportCode(), ""));
            doc.addKeywords("BCP, BCM, SAMA BCF, ISO 22301, lang=" + (wantsAr ? "ar" : "en"));
            doc.open();

            renderCover(doc, in, bilingual, wantsAr);
            renderTableOfContents(doc, bilingual, wantsAr);
            renderPlanMetadata(doc, in, bilingual, wantsAr);
            renderRtoMtpdMatrix(doc, in, bilingual, wantsAr);
            renderSections(doc, in, bilingual, wantsAr);
            renderVersionHistory(doc, in, bilingual, wantsAr);
            renderAppendices(doc, in, bilingual, wantsAr);

            // Capture the page count BEFORE doc.close() since OpenPDF's
            // writer.getPageNumber() returns the *next* page number after
            // close which is misleading by one.
            pageCount = writer.getPageNumber();
            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Binder PDF generation failed", e);
        }

        return new BuildResult(baos.toByteArray(), pageCount);
    }

    // ─── Cover page ────────────────────────────────────────────────

    private void renderCover(Document doc, BuildInput in, boolean bilingual, boolean wantsAr) throws DocumentException {
        BcpPlan p = in.plan();
        Organisation o = in.organisation();
        BcpBinderExport b = in.binder();
        boolean isBcp = !"DRP".equalsIgnoreCase(nz(p.getPlanType(), "BCP"));

        PdfTheme.Cover cv = new PdfTheme.Cover();
        cv.eyebrow    = isBcp ? "Business Continuity Plan" : "Disaster Recovery Plan";
        cv.eyebrowAr  = isBcp ? "خطة استمرارية الأعمال"     : "خطة التعافي من الكوارث";
        cv.code       = p.getPlanCode();
        cv.titleEn    = nz(p.getTitleEn(), "—");
        cv.titleAr    = p.getTitleAr();
        cv.orgNameEn  = o == null ? null : o.getNameEn();
        cv.orgNameAr  = o == null ? null : o.getNameAr();
        cv.classification = "Restricted";
        cv.generatedAt = b.getGeneratedAt() != null ? b.getGeneratedAt() : LocalDateTime.now();
        boolean arDoc = wantsAr && !bilingual;   // pure-AR binder lays out RTL
        cv.ar = arDoc;
        PdfTheme.coverHero(doc, cv);

        // Hijri + Gregorian generation date — Saudi-native dual format.
        // Format example: "23 May 2026 · ١٥ ذو القعدة ١٤٤٧هـ" — the AR
        // half is rendered with the Hijri formatter and Eastern-Arabic
        // numerals. Rendered through the shaping-aware line() so the Hijri
        // month names join their letters (a plain Paragraph never shapes).
        LocalDate gregDate = cv.generatedAt.toLocalDate();
        String hijriAr  = formatHijriArabic(gregDate);
        String gregEn   = gregDate.format(DT_GREG_LONG);
        PdfTheme.line(doc, gregEn + "    ·    " + hijriAr + " هـ", PdfFonts.body(11), arDoc, 4, 14);

        // Responsible officer block — falls back to "—" when the plan
        // doesn't yet carry an owner. Bilingual label always present so
        // a printed copy carries the same field whether the source was
        // EN-only or AR-only.
        PdfPTable meta = PdfTheme.metadataGrid(arDoc);
        PdfTheme.addMetaCell(meta, "Plan type",        nz(p.getPlanType(), "BCP"));
        PdfTheme.addMetaCell(meta, "نوع الخطة",         nz(p.getPlanType(), "BCP"));
        PdfTheme.addMetaCell(meta, "Version",          nz(b.getVersionLabel(), "v1"));
        PdfTheme.addMetaCell(meta, "الإصدار",          nz(b.getVersionLabel(), "v1"));
        PdfTheme.addMetaCell(meta, "Responsible officer",
            nz(p.getApprovedBy(), nz(p.getLastAttestedByName(), "—")));
        PdfTheme.addMetaCell(meta, "المسؤول",
            nz(p.getApprovedBy(), nz(p.getLastAttestedByName(), "—")));
        PdfTheme.addMetaCell(meta, "Export code",      nz(b.getExportCode(), "—"));
        PdfTheme.addMetaCell(meta, "رمز التصدير",      nz(b.getExportCode(), "—"));
        doc.add(meta);

        // Saudi-native distribution banner: AR phrasing "محكوم — غير قابل للتوزيع"
        // is the regulator-recognised wording — same as SAMA RegOnline cover
        // sheets and the AAOIFI shari'ah board template. line() shapes it.
        PdfTheme.line(doc, "مستند محكوم — غير قابل للتوزيع",
            new Font(PdfFonts.baseFont(), 11, Font.BOLD, PdfFonts.DANGER), arDoc, 24, 0);
    }

    // ─── Table of contents ─────────────────────────────────────────

    private void renderTableOfContents(Document doc, boolean bilingual, boolean wantsAr) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Table of Contents", "فهرس المحتويات", null, wantsAr && !bilingual);

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfTheme.runDir(wantsAr && !bilingual));
        try { t.setWidths(new float[] {1, 6, 6}); } catch (DocumentException ignored) {}

        headerCell(t, "#");
        headerCell(t, "Section");
        headerCell(t, "القسم");

        for (int i = 0; i < SECTION_CATALOGUE.size(); i++) {
            SectionDef s = SECTION_CATALOGUE.get(i);
            cell(t, String.valueOf(i + 1));
            cell(t, s.titleEn());
            cell(t, s.titleAr());
        }
        doc.add(t);
    }

    // ─── Plan metadata ─────────────────────────────────────────────

    private void renderPlanMetadata(Document doc, BuildInput in, boolean bilingual, boolean wantsAr) throws DocumentException {
        BcpPlan p = in.plan();
        PdfTheme.sectionDivider(doc, "1. Organisational context", "١. السياق التنظيمي", null, wantsAr && !bilingual);

        Organisation o = in.organisation();
        String orgEn = o == null ? "—" : nz(o.getNameEn(), "—");
        String orgAr = o == null ? "—" : nz(o.getNameAr(), "—");

        addBilingualParagraph(doc, bilingual, wantsAr,
            "This binder describes the business continuity arrangements for " + orgEn
                + ". It is generated offline so the Crisis Room team can rely on it "
                + "when the GRC platform is unavailable.",
            "يصف هذا الكتيّب ترتيبات استمرارية الأعمال في " + orgAr
                + ". أُنشئ ليكون متاحًا دون اتصال بحيث يعتمد عليه فريق غرفة الأزمات "
                + "عند تعذّر الوصول إلى منصة الحوكمة والمخاطر والامتثال.");

        // 2. Purpose & scope
        PdfTheme.sectionDivider(doc, "2. Purpose & scope", "٢. الهدف والنطاق", null, wantsAr && !bilingual);
        addBilingualParagraph(doc, bilingual, wantsAr,
            "Purpose: ensure continuity of " + nz(p.getTitleEn(), "the critical service")
                + " within RTO " + (p.getRtoHours() == null ? "—" : (p.getRtoHours() + "h"))
                + " and RPO " + (p.getRpoHours() == null ? "—" : (p.getRpoHours() + "h")) + ".",
            "الهدف: ضمان استمرارية " + nz(p.getTitleAr(), "الخدمة الحرجة")
                + " ضمن زمن التعافي المستهدف " + (p.getRtoHours() == null ? "—" : (p.getRtoHours() + " ساعة"))
                + " ونقطة التعافي المستهدفة " + (p.getRpoHours() == null ? "—" : (p.getRpoHours() + " ساعة")) + ".");

        // 3. Roles & responsibilities
        PdfTheme.sectionDivider(doc, "3. Roles & responsibilities", "٣. الأدوار والمسؤوليات", null, wantsAr && !bilingual);
        addBilingualParagraph(doc, bilingual, wantsAr,
            "Plan owner: " + nz(p.getApprovedBy(), "—")
                + ". Response team: " + nz(p.getResponseTeamRole(), "—") + ".",
            "مالك الخطة: " + nz(p.getApprovedBy(), "—")
                + ". فريق الاستجابة: " + nz(p.getResponseTeamRole(), "—") + ".");
    }

    // ─── 4. BIA / RTO-MTPD matrix ──────────────────────────────────

    private void renderRtoMtpdMatrix(Document doc, BuildInput in, boolean bilingual, boolean wantsAr) throws DocumentException {
        BcpPlan p = in.plan();
        PdfTheme.sectionDivider(doc, "4. Business Impact Analysis", "٤. تحليل الأثر التجاري", null, wantsAr && !bilingual);

        PdfTheme.kpiStrip(doc, List.of(
            new PdfTheme.Kpi("RTO",  p.getRtoHours()  == null ? "—" : (p.getRtoHours()  + "h"), "Recovery Time"),
            new PdfTheme.Kpi("RPO",  p.getRpoHours()  == null ? "—" : (p.getRpoHours()  + "h"), "Recovery Point"),
            new PdfTheme.Kpi("MTPD", p.getMtpdHours() == null ? "—" : (p.getMtpdHours() + "h"), "Max Tolerable"),
            new PdfTheme.Kpi("Version", "v" + (p.getCurrentVersion() == null ? 1 : p.getCurrentVersion()),
                nz(p.getStatus(), "DRAFT"))
        ), wantsAr && !bilingual);

        addBilingualParagraph(doc, bilingual, wantsAr,
            "The recovery targets above were captured from BIA #"
                + (p.getSourceBiaId() == null ? "—" : p.getSourceBiaId())
                + " at the time of plan approval. They drive the resourcing decisions in the strategy section.",
            "تم استخراج أهداف التعافي أعلاه من تحليل الأثر التجاري رقم "
                + (p.getSourceBiaId() == null ? "—" : p.getSourceBiaId())
                + " وقت اعتماد الخطة، وتُحدِّد القرارات المتعلقة بتخصيص الموارد في قسم الاستراتيجية.");
    }

    // ─── 5–8. Strategy / Recovery / Contacts / Comms (from plan sections) ──

    private void renderSections(Document doc, BuildInput in, boolean bilingual, boolean wantsAr) throws DocumentException {
        // Map plan sections by key (case-insensitive) so we can match the
        // ISO 22301 catalogue to whatever the BcpPlan author actually wrote.
        // Plans use the V107 catalogue (overview/scope/roles/…) — fuzzy match.
        List<BcpSection> secs = in.sections();

        // 5. Continuity strategy
        PdfTheme.sectionDivider(doc, "5. Continuity strategy", "٥. استراتيجية الاستمرارية", null, wantsAr && !bilingual);
        renderMatchedSection(doc, secs, bilingual, wantsAr,
            "recovery_priorities", "strategy", "continuity_strategy");

        // 6. Recovery procedures
        PdfTheme.sectionDivider(doc, "6. Recovery procedures", "٦. إجراءات الاسترداد", null, wantsAr && !bilingual);
        renderMatchedSection(doc, secs, bilingual, wantsAr,
            "recovery_procedures", "recovery", "activation_criteria");

        // 7. Contact tree
        PdfTheme.sectionDivider(doc, "7. Contact tree", "٧. شجرة الاتصال", null, wantsAr && !bilingual);
        renderContactTree(doc, in, bilingual, wantsAr);

        // 8. Crisis communication templates — cross-link to Slice 9-E v2 templates.
        // We don't embed each template body (those evolve independently) — instead
        // we list which template-set the plan ties to and instruct the printed-copy
        // user where the active templates can be found if the platform is online.
        PdfTheme.sectionDivider(doc, "8. Crisis communication templates",
            "٨. قوالب الاتصالات في حالة الأزمات", null, wantsAr && !bilingual);
        addBilingualParagraph(doc, bilingual, wantsAr,
            "Crisis communication templates live in the platform's Crisis Comms registry "
                + "(Slice 9-E). When operating offline, use the contact tree above and the "
                + "regulator-notification stubs provided in the appendices.",
            "تُحفَظ قوالب الاتصالات في حالة الأزمات داخل سجل اتصالات الأزمات في المنصة "
                + "(الشريحة ٩-ﻫ). عند العمل دون اتصال، اعتمد على شجرة الاتصال أعلاه وعلى "
                + "نماذج إبلاغ الجهات التنظيمية المدرجة في الملاحق.");
    }

    /** Locate the first plan section whose key matches any of the requested keys
     *  (case-insensitive) and render its EN/AR body. Falls back to an "—" line. */
    private void renderMatchedSection(Document doc, List<BcpSection> secs,
                                      boolean bilingual, boolean wantsAr,
                                      String... keys) throws DocumentException {
        BcpSection match = null;
        outer:
        for (String want : keys) {
            for (BcpSection s : secs) {
                if (s.getSectionKey() != null && s.getSectionKey().equalsIgnoreCase(want)) {
                    match = s; break outer;
                }
            }
        }
        if (match == null) {
            addBilingualParagraph(doc, bilingual, wantsAr,
                "(Not yet authored — capture this in the plan builder.)",
                "(لم يُكتب بعد — أضِفه في أداة بناء الخطة.)");
            return;
        }
        addBilingualParagraph(doc, bilingual, wantsAr,
            nz(match.getBodyEn(), "(no English body authored)"),
            nz(match.getBodyAr(), "(لم تُدخل النسخة العربية)"));
    }

    private void renderContactTree(Document doc, BuildInput in,
                                   boolean bilingual, boolean wantsAr) throws DocumentException {
        List<BcpPlanAcknowledgement> ack = in.contacts();
        if (ack == null || ack.isEmpty()) {
            addBilingualParagraph(doc, bilingual, wantsAr,
                "(No distribution recipients on file — capture them via the Distribution tab.)",
                "(لا توجد جهات توزيع مسجّلة — أضِفها من تبويب التوزيع.)");
            return;
        }
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfTheme.runDir(wantsAr && !bilingual));
        try { t.setWidths(new float[] {3, 2, 3, 2}); } catch (DocumentException ignored) {}
        headerCell(t, "Recipient / المستلم");
        headerCell(t, "Role / الدور");
        headerCell(t, "Email / البريد");
        headerCell(t, "Channel / القناة");
        for (BcpPlanAcknowledgement a : ack) {
            cell(t, nz(a.getRecipientName(), "—"));
            cell(t, nz(a.getRecipientRole(), "—"));
            cell(t, nz(a.getRecipientEmail(), "—"));
            cell(t, nz(a.getDistributionChannel(), "—"));
        }
        doc.add(t);
    }

    // ─── Version history ────────────────────────────────────────────

    private void renderVersionHistory(Document doc, BuildInput in,
                                      boolean bilingual, boolean wantsAr) throws DocumentException {
        List<BcpVersion> versions = in.versions();
        PdfTheme.sectionDivider(doc, "Version history", "سجل الإصدارات", null, wantsAr && !bilingual);
        if (versions == null || versions.isEmpty()) {
            addBilingualParagraph(doc, bilingual, wantsAr,
                "(No approved versions yet.)",
                "(لا توجد إصدارات معتمدة بعد.)");
            return;
        }
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfTheme.runDir(wantsAr && !bilingual));
        try { t.setWidths(new float[] {1, 3, 3, 6}); } catch (DocumentException ignored) {}
        headerCell(t, "Version");
        headerCell(t, "Approved at");
        headerCell(t, "By");
        headerCell(t, "Change summary");
        for (BcpVersion v : versions) {
            cell(t, "v" + v.getVersionNumber());
            cell(t, v.getApprovedAt() == null ? "—" : v.getApprovedAt().format(DT_FULL));
            cell(t, nz(v.getApprovedBy(), "—"));
            cell(t, nz(v.getChangeSummary(), "—"));
        }
        doc.add(t);
    }

    // ─── 9. Appendices ─────────────────────────────────────────────

    private void renderAppendices(Document doc, BuildInput in,
                                  boolean bilingual, boolean wantsAr) throws DocumentException {
        PdfTheme.sectionDivider(doc, "9. Appendices", "٩. ملاحق", null, wantsAr && !bilingual);
        addBilingualParagraph(doc, bilingual, wantsAr,
            "A1. SAMA RegOnline notification stub — use when reporting an operational "
                + "disruption per SAMA BCF Annex D within the 2-hour clock.",
            "م-١. نموذج إبلاغ ساما RegOnline — يُستخدم عند الإبلاغ عن اضطراب تشغيلي "
                + "وفق ملحق إطار استمرارية الأعمال خلال نافذة الساعتين.");
        addBilingualParagraph(doc, bilingual, wantsAr,
            "A2. NCEMA national alert escalation — Civil Defence + NCEMA hotline for "
                + "incidents that breach physical safety thresholds.",
            "م-٢. تصعيد التنبيهات الوطنية عبر المركز الوطني لإدارة الطوارئ — الدفاع المدني "
                + "والمركز الوطني لإدارة الطوارئ للحوادث التي تتجاوز عتبات السلامة المادية.");
        addBilingualParagraph(doc, bilingual, wantsAr,
            "A3. SDAIA PDPL Article 31 — 72-hour personal-data breach notification.",
            "م-٣. المادة ٣١ من نظام حماية البيانات الشخصية — إبلاغ بانتهاك البيانات الشخصية "
                + "خلال ٧٢ ساعة.");
    }

    // ─── Bilingual layout helpers ──────────────────────────────────

    /** When {@code bilingual} → two side-by-side columns (EN | AR);
     *  otherwise one column with the language matching {@code wantsAr}.
     *  Either side falls back to "—" when null/blank. */
    private void addBilingualParagraph(Document doc, boolean bilingual, boolean wantsAr,
                                       String en, String ar) throws DocumentException {
        if (bilingual) {
            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            try { t.setWidths(new float[] {1, 1}); } catch (DocumentException ignored) {}
            t.setSpacingBefore(4);
            t.setSpacingAfter(8);
            t.addCell(textCell(nz(en, "—"), false));
            t.addCell(textCell(nz(ar, "—"), false));
            doc.add(t);
        } else if (wantsAr) {
            // Shaping-aware + right-aligned — a plain Paragraph rendered the
            // Arabic body disconnected and left-anchored.
            PdfTheme.line(doc, nz(ar, "—"), PdfFonts.body(10), true, 0, 8);
        } else {
            PdfTheme.line(doc, nz(en, "—"), PdfFonts.body(10), false, 0, 8);
        }
    }

    private static PdfPCell textCell(String text, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(text,
            header ? PdfFonts.header(10) : PdfFonts.body(10)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setBorderWidth(0.4f);
        c.setPadding(8);
        // The AR column of a bilingual row must still shape + read from the
        // right; the mirrored ALIGN semantics under RTL make that ALIGN_LEFT.
        if (PdfTheme.hasArabic(text)) {
            c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            c.setHorizontalAlignment(PdfTheme.visualStart(true, PdfWriter.RUN_DIRECTION_RTL));
        }
        return c;
    }

    private static void headerCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(10)));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPadding(6);
        // Arabic headers inside an LTR (bilingual) table need their own RTL run.
        if (PdfTheme.hasArabic(text) && t.getRunDirection() != PdfWriter.RUN_DIRECTION_RTL) {
            c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            c.setHorizontalAlignment(PdfTheme.visualStart(true, PdfWriter.RUN_DIRECTION_RTL));
        }
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(nz(text, "—"), PdfFonts.body(9)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPadding(5);
        if (PdfTheme.hasArabic(text) && t.getRunDirection() != PdfWriter.RUN_DIRECTION_RTL) {
            c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            c.setHorizontalAlignment(PdfTheme.visualStart(true, PdfWriter.RUN_DIRECTION_RTL));
        }
        t.addCell(c);
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static String nz(String s) { return nz(s, "—"); }

    // ─── Date helpers ──────────────────────────────────────────────

    /** Hijri date in Arabic month names, rendered with Eastern-Arabic numerals.
     *  Example: "١٥ ذو القعدة ١٤٤٧". */
    static String formatHijriArabic(LocalDate gregorian) {
        try {
            HijrahDate hd = HijrahDate.from(gregorian);
            String formatted = DT_HIJRI.format(hd);
            return toEasternNumerals(formatted);
        } catch (Exception e) {
            return "—";
        }
    }

    /** Translate ASCII digits ('0'..'9') into Eastern Arabic-Indic digits
     *  ('٠'..'٩'). Non-digit characters pass through unchanged. */
    public static String toEasternNumerals(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append((char) ('٠' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ─── Page event — watermark + footer with Eastern numerals ─────

    /** Watermark + Saudi-native footer page-event helper. Draws:
     *
     * <ul>
     *   <li>A grey rotated watermark on every page (controlled-distribution
     *       wording from the binder row).</li>
     *   <li>A hairline rule above the footer.</li>
     *   <li>Footer left: classification "Restricted".</li>
     *   <li>Footer centre: plan code + version.</li>
     *   <li>Footer right: pagination — "صفحة ٣ من ١٢" with Eastern numerals
     *       when language=AR/BILINGUAL, "Page 3 of 12" otherwise.
     *       Two-pass template pattern is used for the "of Y" total so the
     *       total reflects the actual page count.</li>
     * </ul>
     */
    private static final class BinderPageEvent extends PdfPageEventHelper {
        private final String watermark;
        private final String docLabel;
        private final boolean wantsAr;
        private PdfTemplate totalTemplate;

        BinderPageEvent(String watermark, String docLabel, boolean wantsAr) {
            this.watermark = nz(watermark, "Controlled — Not for Distribution");
            this.docLabel  = nz(docLabel, "");
            this.wantsAr   = wantsAr;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document doc) {
            totalTemplate = writer.getDirectContent().createTemplate(40, 14);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfContentByte over = writer.getDirectContentUnder();
                // ── Watermark ──────────────────────────────────────
                over.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.10f);
                over.setGState(gs);
                Font wmFont = new Font(PdfFonts.baseFont(), 42, Font.BOLD, new Color(0x80, 0x80, 0x80));
                Phrase wm = new Phrase(watermark, wmFont);
                ColumnText.showTextAligned(over, Element.ALIGN_CENTER, wm,
                    (doc.left() + doc.right()) / 2f,
                    (doc.bottom() + doc.top()) / 2f, 45);
                over.restoreState();

                // ── Footer ─────────────────────────────────────────
                PdfContentByte cb = writer.getDirectContent();
                cb.saveState();
                cb.setColorStroke(PdfFonts.HAIRLINE);
                cb.setLineWidth(0.5f);
                cb.moveTo(doc.left(),  doc.bottom() - 10);
                cb.lineTo(doc.right(), doc.bottom() - 10);
                cb.stroke();
                cb.restoreState();

                float baseY = doc.bottom() - 22;

                // Left: classification
                Phrase cls = new Phrase("RESTRICTED",
                    new Font(PdfFonts.baseFont(), 7.5f, Font.BOLD, PdfFonts.BRAND_ACCENT));
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, cls, doc.left(), baseY, 0);

                // Centre: doc label
                if (!docLabel.isBlank()) {
                    Phrase ctr = new Phrase(docLabel, PdfFonts.small(8));
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, ctr,
                        (doc.left() + doc.right()) / 2f, baseY, 0);
                }

                // Right: pagination — Eastern numerals when AR/BILINGUAL
                int n = writer.getPageNumber();
                String pageNum = wantsAr ? toEasternNumerals(String.valueOf(n)) : String.valueOf(n);
                String preText = wantsAr ? ("صفحة " + pageNum + " من ") : ("Page " + n + " of ");
                Phrase right = new Phrase(preText, PdfFonts.small(8));
                float textWidth = PdfFonts.small(8).getBaseFont().getWidthPoint(preText, 8f);
                if (wantsAr) {
                    // Shape the Arabic (plain showTextAligned never does) and put
                    // the "of Y" total on the LEFT — Arabic reads صفحة ٣ من ١٢
                    // right-to-left, so the total is the leftmost run.
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, right,
                        doc.right(), baseY, 0, PdfWriter.RUN_DIRECTION_RTL, 0);
                    cb.addTemplate(totalTemplate, doc.right() - textWidth - 18, baseY - 2);
                } else {
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, right,
                        doc.right() - textWidth - 30, baseY, 0);
                    cb.addTemplate(totalTemplate, doc.right() - 30, baseY - 2);
                }
            } catch (Exception ignored) {
                // never block document close
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                int total = writer.getPageNumber() - 1;
                String totalStr = wantsAr ? toEasternNumerals(String.valueOf(total)) : String.valueOf(total);
                totalTemplate.beginText();
                totalTemplate.setFontAndSize(PdfFonts.small(8).getBaseFont(), 8);
                totalTemplate.setColorFill(PdfFonts.MUTED);
                totalTemplate.showText(totalStr);
                totalTemplate.endText();
            } catch (Exception ignored) {}
        }
    }
}
