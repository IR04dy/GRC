package com.tss.grc.module.reporting.service;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Locates a TrueType font that supports Arabic glyphs and exposes Latin/Arabic
 * Font instances for PDF generation. Probes (in order of precedence):
 *   1. -Dgrc.pdf.arabic-font=/abs/path.ttf  (or env GRC_PDF_ARABIC_FONT) — ops override,
 *      lets a deployment point at a licensed/OSS Arabic TTF without a rebuild
 *   2. classpath:/fonts/&lt;bundled&gt;.ttf   (NotoNaskhArabic / NotoKufiArabic / NotoSansArabic /
 *      Amiri — the first that is actually bundled)
 *   3. C:\Windows\Fonts\arial.ttf            (Windows dev hosts — full Arabic coverage)
 *   4. DejaVuSans.ttf                         (Linux hosts — weak/partial Arabic)
 *   5. Helvetica (built-in Type-1 — Arabic glyphs will appear as boxes)
 *
 * <p>The path the loader settled on is logged at startup so deployment errors
 * (missing Arabic font) surface clearly. NOTE: no Arabic TTF is bundled in the
 * repo by default (licensing) — on a fontless Linux/container host set
 * {@code grc.pdf.arabic-font} or drop an OFL font into {@code resources/fonts/},
 * otherwise Arabic PDFs degrade to DejaVu/Helvetica. See resources/fonts/README.md.
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

    /** Config knob (system property / env var) — an ops override that points at a licensed or
     *  OSS Arabic TTF on the deployment host, taking precedence over every bundled/OS candidate. */
    static final String FONT_PROP = "grc.pdf.arabic-font";
    static final String FONT_ENV  = "GRC_PDF_ARABIC_FONT";

    /** Bundled classpath candidates, tried in order — the first actually present wins. */
    private static final List<String> CLASSPATH_CANDIDATES = List.of(
        "/fonts/NotoNaskhArabic-Regular.ttf",
        "/fonts/NotoKufiArabic-Regular.ttf",
        "/fonts/NotoSansArabic-Regular.ttf",
        "/fonts/Amiri-Regular.ttf"
    );

    /** On-disk OS candidates (last resort before the Latin-only Helvetica fallback). */
    private static final List<String> OS_CANDIDATES = List.of(
        "C:/Windows/Fonts/arial.ttf",
        "C:\\Windows\\Fonts\\arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "/Library/Fonts/Arial Unicode.ttf"
    );

    private static final BaseFont BASE = loadBase();

    /**
     * Resolve an explicit ops-configured font path (system property first, then env var).
     * Returns the path only if set AND the file exists; otherwise null. Package-private + pure
     * ({@code exists} is injected) so it is unit-testable without touching the static font load.
     */
    static String resolveConfiguredFont(String sysProp, String envVal, Predicate<String> exists) {
        for (String p : new String[]{sysProp, envVal}) {
            if (p != null && !p.isBlank() && exists.test(p.trim())) return p.trim();
        }
        return null;
    }

    private static BaseFont loadBase() {
        // 1) Ops override (system property / env) — highest precedence, no rebuild needed.
        String configured = resolveConfiguredFont(
            System.getProperty(FONT_PROP), System.getenv(FONT_ENV), p -> Files.exists(Path.of(p)));
        if (configured != null) {
            try {
                BaseFont bf = BaseFont.createFont(configured, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                log.info("PDF Arabic font: configured {} (via {})", configured,
                    System.getProperty(FONT_PROP) != null ? FONT_PROP : FONT_ENV);
                return bf;
            } catch (Exception e) {
                log.warn("Configured Arabic font '{}' failed to load ({}); falling through to probe.", configured, e.getMessage());
            }
        }

        // 2) Bundled classpath fonts (first present wins).
        for (String cp : CLASSPATH_CANDIDATES) {
            try (InputStream in = PdfFonts.class.getResourceAsStream(cp)) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    BaseFont bf = BaseFont.createFont(cp.substring(cp.lastIndexOf('/') + 1),
                        BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
                    log.info("PDF Arabic font: bundled classpath {}", cp);
                    return bf;
                }
            } catch (Exception e) { /* keep probing */ }
        }

        // 3) OS-installed fonts (arial = full Arabic; DejaVu = weak/partial).
        for (String path : OS_CANDIDATES) {
            try {
                if (Files.exists(Path.of(path))) {
                    BaseFont bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    boolean weak = path.toLowerCase().contains("dejavu");
                    log.warn("PDF Arabic font: using OS font {} ({}). For portable Arabic rendering, "
                        + "set -D{} or bundle an OFL font in resources/fonts/.",
                        path, weak ? "PARTIAL Arabic coverage" : "full Arabic coverage", FONT_PROP);
                    return bf;
                }
            } catch (Exception ignored) { /* keep probing */ }
        }

        // 4) Latin-only fallback — Arabic will render as boxes.
        log.error("No Arabic-capable TrueType font found — falling back to Helvetica; Arabic PDF text "
            + "will render as boxes. Set -D{} (or env {}) to an Arabic TTF, or bundle one in "
            + "resources/fonts/ (see that folder's README).", FONT_PROP, FONT_ENV);
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
