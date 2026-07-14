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
     *  Latin — so table cells/headers don't stay left-aligned under RTL. NOTE:
     *  only valid for elements WITHOUT an RTL run direction; under
     *  {@code RUN_DIRECTION_RTL} OpenPDF mirrors the LEFT/RIGHT semantics — use
     *  {@link #visualStart(boolean, int)} there instead (measured live: an
     *  ALIGN_RIGHT + RTL cover line rendered at x=0.04, the physical left). */
    public static int alignStart(boolean ar) {
        return ar ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    /**
     * The alignment constant that renders at the reading start of the document
     * direction — accounting for OpenPDF mirroring ALIGN_LEFT/RIGHT semantics
     * whenever the element carries {@code RUN_DIRECTION_RTL}: there, ALIGN_LEFT
     * means "start" (visual right) and ALIGN_RIGHT means "end" (visual left).
     */
    public static int visualStart(boolean ar, int runDirection) {
        boolean wantVisualRight = ar;                                        // Arabic docs start at the right
        if (runDirection == PdfWriter.RUN_DIRECTION_RTL) wantVisualRight = !wantVisualRight;
        return wantVisualRight ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    /** True when the string contains any Arabic-block character. Such text MUST be
     *  laid out through a run-direction-aware element (cell/ColumnText with RTL):
     *  a plain {@code doc.add(new Paragraph(..))} never runs OpenPDF's bidi shaper,
     *  so the letters render isolated (disconnected) and in reversed visual order —
     *  the exact defect the Arabic register exports shipped with. */
    public static boolean hasArabic(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F)
                || (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF)) return true;
        }
        return false;
    }

    /**
     * Adds one text line through a borderless single-cell table so the bidi
     * shaper runs. Alignment follows the document direction ({@code ar});
     * shaping engages whenever the text itself is Arabic — an Arabic subtitle
     * inside an English report still joins its letters.
     */
    public static void line(Document doc, String text, Font font, boolean ar,
                            float spacingBefore, float spacingAfter) throws DocumentException {
        if (text == null || text.isBlank()) return;
        PdfPTable w = new PdfPTable(1);
        w.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(0);
        c.setPadding(0);
        c.setUseAscender(true);
        c.setUseDescender(true);
        // Any non-DEFAULT run direction engages the shaper. Base direction: RTL for
        // Arabic-primary docs or Arabic-leading lines; LTR for Latin-leading lines
        // with embedded Arabic (keeps English layout, still joins the Arabic).
        int dir = PdfWriter.RUN_DIRECTION_DEFAULT;
        if (ar || firstStrongIsArabic(text)) dir = PdfWriter.RUN_DIRECTION_RTL;
        else if (hasArabic(text))            dir = PdfWriter.RUN_DIRECTION_LTR;
        if (dir != PdfWriter.RUN_DIRECTION_DEFAULT) c.setRunDirection(dir);
        c.setHorizontalAlignment(visualStart(ar, dir));
        w.addCell(c);
        w.setSpacingBefore(spacingBefore);
        w.setSpacingAfter(spacingAfter);
        doc.add(w);
    }

    /** As {@link #line} but centre-aligned (centre is unaffected by the RTL
     *  alignment mirror) — for footnotes/closings that must still shape Arabic. */
    public static void centeredLine(Document doc, String text, Font font,
                                    float spacingBefore, float spacingAfter) throws DocumentException {
        if (text == null || text.isBlank()) return;
        PdfPTable w = new PdfPTable(1);
        w.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(0);
        c.setPadding(0);
        c.setUseAscender(true);
        c.setUseDescender(true);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (firstStrongIsArabic(text))  c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        else if (hasArabic(text))       c.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        w.addCell(c);
        w.setSpacingBefore(spacingBefore);
        w.setSpacingAfter(spacingAfter);
        doc.add(w);
    }

    /** True when the first strong-directional character of the string is Arabic
     *  (i.e. the line reads as an Arabic sentence, whatever digits/punctuation lead). */
    public static boolean firstStrongIsArabic(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return hasArabic(String.valueOf(c));
        }
        return false;
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
        /** Arabic-primary document — right-aligns the whole hero and lays every
         *  line out RTL. Arabic *Ar lines are shaped even when this is false. */
        public boolean ar;
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

        // Eyebrow — small uppercase brand-accent tag. Every hero line goes through
        // line() so Arabic shapes and the ar flag right-aligns the whole block.
        if (cv.eyebrow != null && !cv.eyebrow.isBlank()) {
            line(doc, cv.eyebrow.toUpperCase(Locale.ROOT), PdfFonts.eyebrow(9), cv.ar, 0, 2);
        }
        line(doc, cv.eyebrowAr, PdfFonts.eyebrow(9), cv.ar, 0, 8);

        // Organisation name — sits just above the title as a smaller line
        line(doc, cv.orgNameEn, PdfFonts.body(11), cv.ar, 0, 0);
        line(doc, cv.orgNameAr, PdfFonts.body(11), cv.ar, 0, 10);

        // Code line (smaller, brand-coloured, sits above the big title)
        line(doc, cv.code, PdfFonts.title(13), cv.ar, 0, 4);

        // Big title — EN first, then AR slightly smaller
        line(doc, cv.titleEn, new Font(PdfFonts.baseFont(), 26, Font.BOLD, brand), cv.ar, 0, 2);
        line(doc, cv.titleAr, new Font(PdfFonts.baseFont(), 18, Font.BOLD, brand), cv.ar, 0, 12);

        // Subtitle / tagline
        line(doc, cv.subtitle, PdfFonts.body(12), cv.ar, 0, 2);
        line(doc, cv.subtitleAr, PdfFonts.body(12), cv.ar, 0, 18);

        // Hairline rule between hero and metadata block
        hairline(doc, 24, 8);

        // Generation stamp — Gregorian + Hijri side by side. The Hijri part is
        // Arabic, so the whole stamp is laid out via a shaping-aware cell.
        String hijri = formatHijri(now.toLocalDate());
        String greg  = now.format(GREG_DATETIME);
        Paragraph stamp = new Paragraph();
        stamp.add(new Phrase(cv.ar ? "أُنشئ  " : "Generated  ", PdfFonts.small(9)));
        stamp.add(new Phrase(greg, PdfFonts.body(10)));
        stamp.add(new Phrase("    ·    ", PdfFonts.small(9)));
        stamp.add(new Phrase("هـ " + hijri, PdfFonts.body(10)));
        PdfPTable stampWrap = new PdfPTable(1);
        stampWrap.setWidthPercentage(100);
        PdfPCell stampCell = new PdfPCell(stamp);
        stampCell.setBorder(0);
        stampCell.setPadding(0);
        stampCell.setUseAscender(true);
        stampCell.setUseDescender(true);
        // RTL base for Arabic covers; LTR base otherwise — either way the shaper
        // runs, so the Hijri segment joins its letters.
        stampCell.setRunDirection(runDir(cv.ar));
        stampCell.setHorizontalAlignment(visualStart(cv.ar, runDir(cv.ar)));
        stampWrap.addCell(stampCell);
        stampWrap.setSpacingAfter(8);
        doc.add(stampWrap);

        // Classification badge — high-contrast pill in the cover for security context
        if (cv.classification != null && !cv.classification.isBlank()) {
            Color cls = classificationColor(cv.classification);
            PdfPTable badge = new PdfPTable(1);
            badge.setHorizontalAlignment(alignStart(cv.ar));
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
        kpiStrip(doc, kpis, false);
    }

    /** RTL-aware variant — mirrors the tile order and shapes Arabic labels. */
    public static void kpiStrip(Document doc, List<Kpi> kpis, boolean ar) throws DocumentException {
        if (kpis == null || kpis.isEmpty()) return;
        int cols = Math.min(4, kpis.size());
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(14);
        t.setRunDirection(runDir(ar));
        for (Kpi k : kpis) t.addCell(kpiTile(k.label, k.value, k.sub, ar));
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
        return kpiTile(label, value, sub, false);
    }

    /** RTL-aware tile — right-aligns under {@code ar} and shapes Arabic text
     *  even inside an LTR document (composite cells never shape otherwise). */
    public static PdfPCell kpiTile(String label, String value, String sub, boolean ar) {
        PdfPCell c = new PdfPCell();
        c.setPadding(10);
        c.setPaddingTop(8);
        c.setPaddingBottom(10);
        c.setBackgroundColor(PdfFonts.PAPER_BG);
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setBorderWidth(0.5f);
        int dir = PdfWriter.RUN_DIRECTION_DEFAULT;
        if (ar || hasArabic(label) || hasArabic(sub)) {
            dir = ar ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;
            c.setRunDirection(dir);
        }
        int align = visualStart(ar, dir);

        Paragraph l = new Paragraph(label == null ? "" : label.toUpperCase(Locale.ROOT),
            PdfFonts.kpiLabel(8));
        l.setSpacingAfter(2);
        l.setAlignment(align);
        c.addElement(l);

        Paragraph v = new Paragraph(value == null ? "—" : value, PdfFonts.kpiValue(20));
        v.setSpacingAfter(2);
        v.setAlignment(align);
        c.addElement(v);

        if (sub != null && !sub.isBlank()) {
            Paragraph s = new Paragraph(sub, PdfFonts.small(8));
            s.setAlignment(align);
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
        sectionDivider(doc, titleEn, titleAr, brand, false);
    }

    /** RTL-aware variant — right-aligns the section titles for Arabic-primary docs;
     *  the Arabic title line is shaped either way. */
    public static void sectionDivider(Document doc, String titleEn, String titleAr, Color brand, boolean ar) throws DocumentException {
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

        boolean hasAr = titleAr != null && !titleAr.isBlank();
        line(doc, titleEn == null ? "" : titleEn, PdfFonts.title(15), ar, 0, hasAr ? 2 : 12);
        line(doc, titleAr, PdfFonts.bold(12), ar, 0, 12);
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
        if (hasArabic(text)) c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);   // shape Arabic labels
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
        return metadataGrid(false);
    }

    /** RTL-aware variant — mirrors the label/value column order for Arabic docs. */
    public static PdfPTable metadataGrid(boolean ar) {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setRunDirection(runDir(ar));
        try { t.setWidths(new float[] {1.1f, 2, 1.1f, 2}); } catch (DocumentException ignored) {}
        return t;
    }

    /** Adds a label/value pair to a {@link #metadataGrid()} table. Alignment and
     *  shaping follow the grid's run direction; Arabic values shape even inside
     *  an LTR grid (e.g. the Arabic org name on an English cover). */
    public static void addMetaCell(PdfPTable t, String label, String value) {
        boolean rtl = t.getRunDirection() == PdfWriter.RUN_DIRECTION_RTL;
        PdfPCell l = new PdfPCell(new Phrase(label == null ? "" : label.toUpperCase(Locale.ROOT),
            PdfFonts.kpiLabel(8)));
        PdfPCell v = new PdfPCell(new Phrase(value == null || value.isBlank() ? "—" : value,
            PdfFonts.body(10)));
        l.setBorder(0); v.setBorder(0);
        l.setPaddingTop(4); l.setPaddingBottom(8);
        v.setPaddingTop(4); v.setPaddingBottom(8);
        // Cells inherit the grid's run direction; ALIGN under RTL is mirrored,
        // so "start of reading" is visualStart(), not a literal RIGHT.
        int dir = rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_DEFAULT;
        l.setHorizontalAlignment(visualStart(rtl, dir));
        v.setHorizontalAlignment(visualStart(rtl, dir));
        if (!rtl && hasArabic(value)) v.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
        if (!rtl && hasArabic(label)) l.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
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
                    showFooterText(cb, Element.ALIGN_LEFT, cls, doc.left(), baseY, classification);
                }
                // Center: doc label
                if (!docLabel.isBlank()) {
                    Phrase ctr = new Phrase(docLabel, PdfFonts.small(8));
                    showFooterText(cb, Element.ALIGN_CENTER, ctr,
                        (doc.left() + doc.right()) / 2f, baseY, docLabel);
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

        /** {@code showTextAligned} without a run direction never shapes Arabic —
         *  route Arabic-bearing footer strings through the RTL-aware overload. */
        private static void showFooterText(PdfContentByte cb, int align, Phrase p,
                                           float x, float y, String raw) {
            if (hasArabic(raw)) {
                ColumnText.showTextAligned(cb, align, p, x, y, 0, PdfWriter.RUN_DIRECTION_RTL, 0);
            } else {
                ColumnText.showTextAligned(cb, align, p, x, y, 0);
            }
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
