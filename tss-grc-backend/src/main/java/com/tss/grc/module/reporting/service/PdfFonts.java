package com.tss.grc.module.reporting.service;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Locates a TrueType font that supports Arabic glyphs and exposes Latin/Arabic
 * Font instances for PDF generation. Probes (in order):
 *   1. classpath:/fonts/NotoSansArabic-Regular.ttf  (preferred — bundled if present)
 *   2. C:\Windows\Fonts\arial.ttf                    (Windows hosts)
 *   3. /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf (Linux hosts)
 *   4. Helvetica (built-in Type-1 — Arabic glyphs will appear as boxes)
 *
 * <p>The path the loader settled on is logged at startup so deployment errors
 * (missing Arabic font) surface clearly.
 *
 * <p>Also exposes the shared executive-PDF colour palette so every exporter
 * uses the same brand teal, gold-accent rule, warm-paper KPI background, and
 * traffic-light status colours. Callers should prefer these constants over
 * inlining raw hex values, so the whole platform's PDF output stays visually
 * consistent and can be re-themed in one place.
 */
@Slf4j
public final class PdfFonts {

    // ── Brand palette (single source of truth for every PDF exporter) ────
    /** Deep teal — primary brand, used for titles, KPI values, table headers. */
    public static final Color BRAND_PRIMARY = new Color(0x09, 0x42, 0x42);
    /** Sand-gold — accent for eyebrows, hairlines, classification badges. */
    public static final Color BRAND_ACCENT  = new Color(0xC9, 0xA9, 0x77);
    /** Warm grey — body subtitles, small print, footer metadata. */
    public static final Color MUTED         = new Color(0x5C, 0x6A, 0x65);
    /** Near-black — primary body copy (better contrast than DARK_GRAY). */
    public static final Color INK           = new Color(0x1A, 0x1A, 0x1A);
    /** Warm cream — KPI tile background. */
    public static final Color PAPER_BG      = new Color(0xFA, 0xF8, 0xF4);
    /** Slightly darker cream — KPI tile border. */
    public static final Color PAPER_BORDER  = new Color(0xE5, 0xDF, 0xD2);
    /** Hairline rule — section dividers, footer separator. */
    public static final Color HAIRLINE      = new Color(0xD6, 0xCE, 0xBC);
    /** White paper colour (KPI tile value-on-dark, header text). */
    public static final Color PAPER         = Color.WHITE;
    // ── Status palette (traffic-light chips + severity colours) ──────────
    public static final Color SUCCESS = new Color(0x2E, 0x7D, 0x32);
    public static final Color WARNING = new Color(0xC7, 0x77, 0x00);
    public static final Color DANGER  = new Color(0xC0, 0x39, 0x2B);
    public static final Color INFO    = new Color(0x1D, 0x4E, 0x89);
    public static final Color NEUTRAL = new Color(0x6B, 0x72, 0x80);

    private static final List<String> CANDIDATES = List.of(
        "/fonts/NotoSansArabic-Regular.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "C:\\Windows\\Fonts\\arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "/Library/Fonts/Arial Unicode.ttf"
    );

    private static final BaseFont BASE = loadBase();

    private static BaseFont loadBase() {
        // Try classpath
        try (InputStream in = PdfFonts.class.getResourceAsStream(CANDIDATES.get(0))) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                BaseFont bf = BaseFont.createFont("NotoSansArabic.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
                log.info("PDF Arabic font: bundled NotoSansArabic-Regular");
                return bf;
            }
        } catch (Exception e) { /* keep probing */ }

        for (int i = 1; i < CANDIDATES.size(); i++) {
            String path = CANDIDATES.get(i);
            try {
                if (Files.exists(Path.of(path))) {
                    BaseFont bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    log.info("PDF Arabic font: {}", path);
                    return bf;
                }
            } catch (Exception ignored) { /* keep probing */ }
        }

        log.warn("No Arabic-capable TrueType font found — falling back to Helvetica. Arabic text will not render correctly. Drop a TTF into src/main/resources/fonts/NotoSansArabic-Regular.ttf to fix.");
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load any base font", e);
        }
    }

    /** Direct access to the loaded BaseFont — useful when building custom
     *  Font instances with non-default colours (e.g. an inverted chip). */
    public static BaseFont baseFont() { return BASE; }

    // ── Generic factory ──────────────────────────────────────────────────
    /** Open-ended factory — any size, style, colour. Prefer the named
     *  helpers below unless you genuinely need a one-off. */
    public static Font font(float size, int style, Color color) {
        return new Font(BASE, size, style, color);
    }

    // ── Named typography ────────────────────────────────────────────────
    public static Font normal(float size) {
        return new Font(BASE, size, Font.NORMAL, Color.DARK_GRAY);
    }
    public static Font bold(float size) {
        return new Font(BASE, size, Font.BOLD, BRAND_PRIMARY);
    }
    public static Font header(float size) {
        return new Font(BASE, size, Font.BOLD, PAPER);
    }
    public static Font small(float size) {
        return new Font(BASE, size, Font.NORMAL, MUTED);
    }

    /** Executive cover-page title (organisation/document title). */
    public static Font title(float size) {
        return new Font(BASE, size, Font.BOLD, BRAND_PRIMARY);
    }

    /** Eyebrow — small uppercase tag above a title (REPORT TYPE etc.). */
    public static Font eyebrow(float size) {
        return new Font(BASE, size, Font.BOLD, BRAND_ACCENT);
    }

    /** KPI tile value (large bold brand-coloured number). */
    public static Font kpiValue(float size) {
        return new Font(BASE, size, Font.BOLD, BRAND_PRIMARY);
    }

    /** KPI tile label — small uppercase muted caption. */
    public static Font kpiLabel(float size) {
        return new Font(BASE, size, Font.BOLD, MUTED);
    }

    /** Body copy — better contrast than {@link #normal(float)}. */
    public static Font body(float size) {
        return new Font(BASE, size, Font.NORMAL, INK);
    }

    /** White text used inside coloured chips and dark headers. */
    public static Font white(float size, boolean bold) {
        return new Font(BASE, size, bold ? Font.BOLD : Font.NORMAL, PAPER);
    }

    private PdfFonts() {}
}
