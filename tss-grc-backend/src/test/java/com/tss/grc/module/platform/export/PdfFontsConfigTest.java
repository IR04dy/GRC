package com.tss.grc.module.platform.export;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export P0-1 — the Arabic-font ops override resolver. Verifies precedence
 * (system property &gt; env var), existence filtering, and blank/null handling,
 * so a deployment can point {@code grc.pdf.arabic-font} / GRC_PDF_ARABIC_FONT at
 * a licensed/OSS Arabic TTF without a rebuild.
 */
class PdfFontsConfigTest {

    private static final Predicate<String> EXISTS = Set.of("/opt/fonts/noto.ttf", "/opt/fonts/amiri.ttf")::contains;

    @Test
    void systemPropertyWinsWhenPresent() {
        assertThat(PdfFonts.resolveConfiguredFont("/opt/fonts/noto.ttf", "/opt/fonts/amiri.ttf", EXISTS))
            .isEqualTo("/opt/fonts/noto.ttf");
    }

    @Test
    void fallsBackToEnvWhenNoSystemProperty() {
        assertThat(PdfFonts.resolveConfiguredFont(null, "/opt/fonts/amiri.ttf", EXISTS))
            .isEqualTo("/opt/fonts/amiri.ttf");
    }

    @Test
    void skipsConfiguredPathThatDoesNotExist() {
        // system property points at a missing file → fall through to the (existing) env font
        assertThat(PdfFonts.resolveConfiguredFont("/nope/missing.ttf", "/opt/fonts/amiri.ttf", EXISTS))
            .isEqualTo("/opt/fonts/amiri.ttf");
    }

    @Test
    void returnsNullWhenNothingConfiguredOrPresent() {
        assertThat(PdfFonts.resolveConfiguredFont(null, null, EXISTS)).isNull();
        assertThat(PdfFonts.resolveConfiguredFont("  ", "", EXISTS)).isNull();
        assertThat(PdfFonts.resolveConfiguredFont("/nope/a.ttf", "/nope/b.ttf", EXISTS)).isNull();
    }
}
