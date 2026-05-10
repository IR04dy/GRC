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
 * The path the loader settled on is logged at startup so deployment errors
 * (missing Arabic font) surface clearly.
 */
@Slf4j
public final class PdfFonts {

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

    public static Font normal(float size) {
        return new Font(BASE, size, Font.NORMAL, Color.DARK_GRAY);
    }
    public static Font bold(float size) {
        return new Font(BASE, size, Font.BOLD, new Color(0x09, 0x42, 0x42));
    }
    public static Font header(float size) {
        return new Font(BASE, size, Font.BOLD, new Color(0xFF, 0xFF, 0xFF));
    }
    public static Font small(float size) {
        return new Font(BASE, size, Font.NORMAL, new Color(0x5C, 0x6A, 0x65));
    }

    private PdfFonts() {}
}
