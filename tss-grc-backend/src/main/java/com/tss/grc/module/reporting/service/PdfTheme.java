package com.tss.grc.module.reporting.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Shared executive-PDF rendering primitives — one source of truth for the
 * "leadership-grade" look that every TSS GRC report exporter inherits.
 *
 * <p>Everything in here is stateless and static; it builds OpenPDF
 * {@link com.lowagie.text} elements that callers stitch into their own
 * documents. The goal is a consistent visual identity across the BCP/DRP
 * plan, crisis after-action, NDI reports, board pack, and any future
 * exporter — without locking each one into a rigid template.
 *
 * <p>Primitives provided:
 * <ul>
 *   <li>{@link #coverHero} — branded cover band with eyebrow tag, large
 *       bilingual title, organisation name, Hijri + Gregorian date stamp,
 *       and classification badge.</li>
 *   <li>{@link #kpiStrip} / {@link #kpiTile} — a row of 4 metric tiles
 *       (label / large value / sub-caption) for executive-summary numbers.</li>
 *   <li>{@link #sectionDivider} — full-width branded section header with
 *       optional Arabic subtitle, designed to start a new logical page.</li>
 *   <li>{@link #statusChip} — small coloured pill containing a status or
 *       severity label, with traffic-light colouring keyed off the value.</li>
 *   <li>{@link #metadataGrid} — borderless 2-column key/value grid for cover
 *       metadata blocks.</li>
 *   <li>{@link #professionalFooter} — page-event helper that draws a
 *       hairline rule + "Document · Classification · Page X of Y" footer
 *       across every page, with two-pass total-page rendering.</li>
 * </ul>
 *
 * <p>All primitives are LTR by default; pass {@code ar = true} where a
 * helper supports it to flip alignment for Arabic-primary documents.
 * Within a single document, Latin and Arabic text can coexist — the font
 * shaper handles RTL glyph direction at run level.
 */
public final class PdfTheme {

    /** Saudi Hijri calendar formatter (HijrahDate is JDK-provided, ISO-8601 style). */
    private static final DateTimeFormatter HIJRI_FMT =
        DateTimeFormatter.ofPattern("yyyy/MM/dd", new Locale("ar", "SA")).withChronology(java.time.chrono.HijrahChronology.INSTANCE);
    private static final DateTimeFormatter GREG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter GREG_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PdfTheme() {}

    // ── RTL / LTR run direction (export remediation P0-2) ────────────────────
    /** OpenPDF run direction for a report language. Apply to the {@code PdfWriter}
     *  (covers ColumnText/paragraphs) AND each {@code PdfPTable} (cells inherit the
     *  table's direction) so Arabic reports lay out right-to-left, not just with
     *  translated labels. Mirrors the reference RTL implementation in BoardPackService. */
    public static int runDir(boolean ar) {
        return ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;
    }

    /** Horizontal alignment matching the run direction — RIGHT for Arabic, LEFT for
     *  Latin — so table cells/headers don't stay left-aligned under RTL. */
    public static int alignStart(boolean ar) {
        return ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    // ────────────────────────────────────────────────────────────────────
    // Cover hero — branded band + title + org + dates + classification
    // ────────────────────────────────────────────────────────────────────

    /** Bundle of data the cover hero needs. Mutable on purpose so callers
     *  can build it incrementally without a long constructor.
     *  All fields are optional; null/blank values are skipped. */
    public static final class Cover {
        /** Small all-caps tag above the title (e.g. "BUSINESS CONTINUITY PLAN"). */
        public String eyebrow;
        /** Same tag in Arabic — rendered below the English eyebrow. */
        public String eyebrowAr;
        /** Code or identifier line (e.g. "BCP-2026-007"). Optional. */
        public String code;
        /** Primary English title — the big bold line. */
        public String titleEn;
        /** Primary Arabic title — rendered immediately below in slightly smaller bold. */
        public String titleAr;
        /** Optional one-line tagline / subtitle (English). */
        public String subtitle;
        /** Same tagline in Arabic. */
        public String subtitleAr;
        /** Tenant organisation name (English). */
        public String orgNameEn;
        /** Tenant organisation name (Arabic). */
        public String orgNameAr;
        /** Classification banner — e.g. "CONFIDENTIAL", "INTERNAL", "RESTRICTED". */
        public String classification;
        /** When the document was generated; defaults to {@code now()} if null. */
        public LocalDateTime generatedAt;
        /** Optional brand color override (per-tenant). Defaults to {@link PdfFonts#BRAND_PRIMARY}. */
        public Color brand;
    }

    /** Renders the executive cover hero. Adds a 8-mm coloured band, the eyebrow
     *  tag, organisation name, title (EN + AR), subtitle, and the Hijri +
     *  Gregorian generation stamp + classification badge in the lower-third. */
    public static void coverHero(Document doc, Cover cv) throws DocumentException {
        Color brand = cv.brand != null ? cv.brand : PdfFonts.BRAND_PRIMARY;
        LocalDateTime now = cv.generatedAt != null ? cv.generatedAt : LocalDateTime.now();

        // Brand band — a full-width filled bar at the top of the page. We draw
        // it as a borderless coloured cell so it stretches edge-to-edge inside
        // the body margin. (For a true bleed we'd use a PdfContentByte rectangle
        // before doc.open() — that's heavier than this needs.)
        PdfPTable bar = new PdfPTable(1);
        bar.setWidthPercentage(100);
        PdfPCell bandCell = new PdfPCell(new Phrase(" "));
        bandCell.setFixedHeight(6f);
        bandCell.setBorder(0);
        bandCell.setBackgroundColor(brand);
        bar.addCell(bandCell);
        bar.setSpacingAfter(28);
        doc.add(bar);

        // Eyebrow — small uppercase brand-accent tag
        if (cv.eyebrow != null && !cv.eyebrow.isBlank()) {
            Paragraph eb = new Paragraph(cv.eyebrow.toUpperCase(Locale.ROOT), PdfFonts.eyebrow(9));
            eb.setSpacingAfter(2);
            doc.add(eb);
        }
        if (cv.eyebrowAr != null && !cv.eyebrowAr.isBlank()) {
            Paragraph eba = new Paragraph(cv.eyebrowAr, PdfFonts.eyebrow(9));
            eba.setSpacingAfter(8);
            doc.add(eba);
        }

        // Organisation name — sits just above the title as a smaller line
        if (cv.orgNameEn != null && !cv.orgNameEn.isBlank()) {
            Paragraph org = new Paragraph(cv.orgNameEn, PdfFonts.body(11));
            org.setSpacingAfter(0);
            doc.add(org);
        }
        if (cv.orgNameAr != null && !cv.orgNameAr.isBlank()) {
            Paragraph orgA = new Paragraph(cv.orgNameAr, PdfFonts.body(11));
            orgA.setSpacingAfter(10);
            doc.add(orgA);
        }

        // Code line (smaller, brand-coloured, sits above the big title)
        if (cv.code != null && !cv.code.isBlank()) {
            Paragraph c = new Paragraph(cv.code, PdfFonts.title(13));
            c.setSpacingAfter(4);
            doc.add(c);
        }

        // Big title — EN first, then AR slightly smaller
        if (cv.titleEn != null && !cv.titleEn.isBlank()) {
            Paragraph t = new Paragraph(cv.titleEn,
                new Font(PdfFonts.baseFont(), 26, Font.BOLD, brand));
            t.setSpacingAfter(2);
            doc.add(t);
        }
        if (cv.titleAr != null && !cv.titleAr.isBlank()) {
            Paragraph ta = new Paragraph(cv.titleAr,
                new Font(PdfFonts.baseFont(), 18, Font.BOLD, brand));
            ta.setSpacingAfter(12);
            doc.add(ta);
        }

        // Subtitle / tagline
        if (cv.subtitle != null && !cv.subtitle.isBlank()) {
            Paragraph s = new Paragraph(cv.subtitle, PdfFonts.body(12));
            s.setSpacingAfter(2);
            doc.add(s);
        }
        if (cv.subtitleAr != null && !cv.subtitleAr.isBlank()) {
            Paragraph sa = new Paragraph(cv.subtitleAr, PdfFonts.body(12));
            sa.setSpacingAfter(18);
            doc.add(sa);
        }

        // Hairline rule between hero and metadata block
        hairline(doc, 24, 8);

        // Generation stamp — Gregorian + Hijri side by side
        String hijri = formatHijri(now.toLocalDate());
        String greg  = now.format(GREG_DATETIME);
        Paragraph stamp = new Paragraph();
        stamp.add(new Phrase("Generated  ", PdfFonts.small(9)));
        stamp.add(new Phrase(greg, PdfFonts.body(10)));
        stamp.add(new Phrase("    ·    ", PdfFonts.small(9)));
        stamp.add(new Phrase("هـ " + hijri, PdfFonts.body(10)));
        stamp.setSpacingAfter(8);
        doc.add(stamp);

        // Classification badge — high-contrast pill in the cover for security context
        if (cv.classification != null && !cv.classification.isBlank()) {
            Color cls = classificationColor(cv.classification);
            PdfPTable badge = new PdfPTable(1);
            badge.setHorizontalAlignment(Element.ALIGN_LEFT);
            try { badge.setTotalWidth(150); badge.setLockedWidth(true); } catch (Exception ignored) {}
            PdfPCell c = new PdfPCell(new Phrase(cv.classification.toUpperCase(Locale.ROOT),
                PdfFonts.white(8.5f, true)));
            c.setBackgroundColor(cls);
            c.setBorder(0);
            c.setPadding(5);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            badge.addCell(c);
            badge.setSpacingAfter(20);
            doc.add(badge);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // KPI strip — row of metric tiles for executive summaries
    // ────────────────────────────────────────────────────────────────────

    /** Single KPI definition for {@link #kpiStrip(Document, List)}. */
    public static final class Kpi {
        public final String label;
        public final String value;
        public final String sub;
        public Kpi(String label, String value, String sub) {
            this.label = label;
            this.value = value;
            this.sub = sub == null ? "" : sub;
        }
    }

    /** Renders a 1-row strip of KPI tiles. Lays them out in a 4-column table;
     *  callers should pass 2 / 3 / 4 / 6 / 8 tiles (multiples that wrap cleanly). */
    public static void kpiStrip(Document doc, List<Kpi> kpis) throws DocumentException {
        if (kpis == null || kpis.isEmpty()) return;
        int cols = Math.min(4, kpis.size());
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(14);
        for (Kpi k : kpis) t.addCell(kpiTile(k.label, k.value, k.sub));
        // Pad to a full row of `cols` for visual alignment
        int remainder = kpis.size() % cols;
        if (remainder != 0) {
            for (int i = remainder; i < cols; i++) {
                PdfPCell blank = new PdfPCell(new Phrase(" "));
                blank.setBorder(0);
                t.addCell(blank);
            }
        }
        doc.add(t);
    }

    /** Single KPI tile cell — warm-paper background, brand border, big bold
     *  value, small uppercase label, optional sub-caption. */
    public static PdfPCell kpiTile(String label, String value, String sub) {
        PdfPCell c = new PdfPCell();
        c.setPadding(10);
        c.setPaddingTop(8);
        c.setPaddingBottom(10);
        c.setBackgroundColor(PdfFonts.PAPER_BG);
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setBorderWidth(0.5f);

        Paragraph l = new Paragraph(label == null ? "" : label.toUpperCase(Locale.ROOT),
            PdfFonts.kpiLabel(8));
        l.setSpacingAfter(2);
        c.addElement(l);

        Paragraph v = new Paragraph(value == null ? "—" : value, PdfFonts.kpiValue(20));
        v.setSpacingAfter(2);
        c.addElement(v);

        if (sub != null && !sub.isBlank()) {
            Paragraph s = new Paragraph(sub, PdfFonts.small(8));
            c.addElement(s);
        }
        return c;
    }

    // ────────────────────────────────────────────────────────────────────
    // Section divider — brand-coloured header with optional Arabic line
    // ────────────────────────────────────────────────────────────────────

    /** Branded section header. Calls {@code doc.newPage()} first so each
     *  section starts on a fresh sheet — match the visual rhythm of the
     *  board pack. Pass titleAr=null to skip the Arabic line. */
    public static void sectionDivider(Document doc, String titleEn, String titleAr) throws DocumentException {
        sectionDivider(doc, titleEn, titleAr, PdfFonts.BRAND_PRIMARY);
    }

    /** Brand-colour-aware section divider — pass the tenant's primary colour so section
     *  bands match the cover. Falls back to {@link PdfFonts#BRAND_PRIMARY} when {@code brand}
     *  is null. */
    public static void sectionDivider(Document doc, String titleEn, String titleAr, Color brand) throws DocumentException {
        Color band = brand != null ? brand : PdfFonts.BRAND_PRIMARY;
        doc.newPage();
        // Brand band — narrow filled cell across the top of the new page
        PdfPTable bar = new PdfPTable(1);
        bar.setWidthPercentage(100);
        PdfPCell bandCell = new PdfPCell(new Phrase(" "));
        bandCell.setFixedHeight(4f);
        bandCell.setBorder(0);
        bandCell.setBackgroundColor(band);
        bar.addCell(bandCell);
        bar.setSpacingAfter(12);
        doc.add(bar);

        Paragraph t = new Paragraph(titleEn == null ? "" : titleEn, PdfFonts.title(15));
        t.setSpacingAfter(2);
        doc.add(t);
        if (titleAr != null && !titleAr.isBlank()) {
            Paragraph ta = new Paragraph(titleAr, PdfFonts.bold(12));
            ta.setSpacingAfter(12);
            doc.add(ta);
        } else {
            t.setSpacingAfter(12);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Status chip — small coloured pill
    // ────────────────────────────────────────────────────────────────────

    /** Renders a small coloured chip for a status/severity. Embedded in
     *  a 1-cell table so it sits inline at its natural width. */
    public static PdfPTable statusChip(String text, ChipKind kind) {
        PdfPTable t = new PdfPTable(1);
        try { t.setTotalWidth(80); t.setLockedWidth(true); } catch (Exception ignored) {}
        t.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell c = new PdfPCell(new Phrase(text == null ? "—" : text,
            PdfFonts.white(8, true)));
        c.setBackgroundColor(chipColor(kind));
        c.setBorder(0);
        c.setPadding(4);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
        return t;
    }

    public enum ChipKind { SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

    private static Color chipColor(ChipKind k) {
        if (k == null) return PdfFonts.NEUTRAL;
        return switch (k) {
            case SUCCESS -> PdfFonts.SUCCESS;
            case WARNING -> PdfFonts.WARNING;
            case DANGER  -> PdfFonts.DANGER;
            case INFO    -> PdfFonts.INFO;
            case NEUTRAL -> PdfFonts.NEUTRAL;
        };
    }

    /** Maps common status/severity strings to a chip colour. Case-insensitive
     *  and tolerant of nulls — falls back to NEUTRAL if no match. */
    public static ChipKind kindOf(String value) {
        if (value == null) return ChipKind.NEUTRAL;
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "CRITICAL", "5", "BLOCKED", "BREACHED", "OVERDUE", "REJECTED", "FAILED",
                 "CAB_REJECTED", "ROLLED_BACK"            -> ChipKind.DANGER;
            case "HIGH", "4", "WARNING", "AT_RISK", "PENDING_REVIEW",
                 "UNDER_REVIEW", "PENDING", "CAB_PENDING" -> ChipKind.WARNING;
            case "APPROVED", "PUBLISHED", "ACTIVE", "RESOLVED", "CLOSED", "COMPLETED",
                 "IMPLEMENTED", "CAB_APPROVED", "SUCCESS"  -> ChipKind.SUCCESS;
            case "DRAFT", "NEW", "OPEN", "SUBMITTED", "IN_PROGRESS",
                 "IN_REMEDIATION", "INVESTIGATING"        -> ChipKind.INFO;
            default                                        -> ChipKind.NEUTRAL;
        };
    }

    // ────────────────────────────────────────────────────────────────────
    // Metadata grid — borderless 2/4-column key-value block
    // ────────────────────────────────────────────────────────────────────

    /** 4-column borderless metadata grid (label, value, label, value). Returns
     *  the configured table so callers can keep adding rows via
     *  {@link #addMetaCell}. */
    public static PdfPTable metadataGrid() {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        try { t.setWidths(new float[] {1.1f, 2, 1.1f, 2}); } catch (DocumentException ignored) {}
        return t;
    }

    /** Adds a label/value pair to a {@link #metadataGrid()} table. */
    public static void addMetaCell(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label == null ? "" : label.toUpperCase(Locale.ROOT),
            PdfFonts.kpiLabel(8)));
        PdfPCell v = new PdfPCell(new Phrase(value == null || value.isBlank() ? "—" : value,
            PdfFonts.body(10)));
        l.setBorder(0); v.setBorder(0);
        l.setPaddingTop(4); l.setPaddingBottom(8);
        v.setPaddingTop(4); v.setPaddingBottom(8);
        t.addCell(l); t.addCell(v);
    }

    // ────────────────────────────────────────────────────────────────────
    // Hairline rule — subtle horizontal divider
    // ────────────────────────────────────────────────────────────────────

    /** A thin sand-coloured horizontal rule with caller-controlled spacing. */
    public static void hairline(Document doc, float spaceBefore, float spaceAfter) throws DocumentException {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" "));
        c.setFixedHeight(0.7f);
        c.setBackgroundColor(PdfFonts.HAIRLINE);
        c.setBorder(0);
        rule.addCell(c);
        rule.setSpacingBefore(spaceBefore);
        rule.setSpacingAfter(spaceAfter);
        doc.add(rule);
    }

    // ────────────────────────────────────────────────────────────────────
    // Professional footer — hairline + label + Page X of Y on every page
    // ────────────────────────────────────────────────────────────────────

    /** Build the page-event helper for a professional footer. Renders
     *  Document · Classification · Page X of Y on every page, with a
     *  thin sand rule above. Uses OpenPDF's two-pass template pattern
     *  so the "of Y" total reflects the actual page count. */
    public static PdfPageEventHelper professionalFooter(String docLabel, String classification) {
        return new ProfessionalFooter(docLabel, classification);
    }

    private static final class ProfessionalFooter extends PdfPageEventHelper {
        private final String docLabel;
        private final String classification;
        private PdfTemplate totalTemplate;

        ProfessionalFooter(String docLabel, String classification) {
            this.docLabel = docLabel == null ? "" : docLabel;
            this.classification = classification == null ? "" : classification;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalTemplate = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                // Hairline above footer
                cb.saveState();
                cb.setColorStroke(PdfFonts.HAIRLINE);
                cb.setLineWidth(0.5f);
                cb.moveTo(doc.left(), doc.bottom() - 10);
                cb.lineTo(doc.right(), doc.bottom() - 10);
                cb.stroke();
                cb.restoreState();

                float baseY = doc.bottom() - 22;
                // Left: classification (uppercase, brand accent if set)
                if (!classification.isBlank()) {
                    Phrase cls = new Phrase(classification.toUpperCase(Locale.ROOT),
                        new Font(PdfFonts.baseFont(), 7.5f, Font.BOLD, PdfFonts.BRAND_ACCENT));
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, cls, doc.left(), baseY, 0);
                }
                // Center: doc label
                if (!docLabel.isBlank()) {
                    Phrase ctr = new Phrase(docLabel, PdfFonts.small(8));
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, ctr,
                        (doc.left() + doc.right()) / 2f, baseY, 0);
                }
                // Right: "Page X of "+ template
                String pageText = "Page " + writer.getPageNumber() + " of ";
                float textWidth = PdfFonts.small(8).getBaseFont().getWidthPoint(pageText, 8f);
                Phrase right = new Phrase(pageText, PdfFonts.small(8));
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, right,
                    doc.right() - textWidth - 24, baseY, 0);
                cb.addTemplate(totalTemplate, doc.right() - 24, baseY - 2);
            } catch (Exception ignored) { /* never block document close */ }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                totalTemplate.beginText();
                totalTemplate.setFontAndSize(PdfFonts.small(8).getBaseFont(), 8);
                totalTemplate.setColorFill(PdfFonts.MUTED);
                // writer.getPageNumber() returns the *next* page number at close, so
                // the actual total is one less than that. (See OpenPDF docs.)
                totalTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
                totalTemplate.endText();
            } catch (Exception ignored) {}
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Date / formatting helpers
    // ────────────────────────────────────────────────────────────────────

    /** Formats a Gregorian date as "1447/11/04" in the Saudi Hijri calendar.
     *  Wraps the HijrahDate JDK API so callers don't have to import it. */
    public static String formatHijri(LocalDate gregorian) {
        try {
            HijrahDate hd = HijrahDate.from(gregorian);
            return HIJRI_FMT.format(hd);
        } catch (Exception e) {
            return "—";
        }
    }

    /** Maps classification strings to a banner colour. Defaults to muted. */
    private static Color classificationColor(String classification) {
        if (classification == null) return PdfFonts.NEUTRAL;
        String c = classification.trim().toUpperCase(Locale.ROOT);
        return switch (c) {
            case "PUBLIC"                 -> PdfFonts.SUCCESS;
            case "INTERNAL"               -> PdfFonts.INFO;
            case "CONFIDENTIAL"           -> PdfFonts.WARNING;
            case "RESTRICTED", "SECRET"   -> PdfFonts.DANGER;
            default                       -> PdfFonts.NEUTRAL;
        };
    }

    // Public so exporters can use the standard format for inline date stamps too
    public static final DateTimeFormatter DATE_LONG = GREG_DATE;
    public static final DateTimeFormatter DATETIME = GREG_DATETIME;
}
