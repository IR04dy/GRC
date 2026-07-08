package com.tss.grc.module.platform.branding.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export P2 — hex → colour parsing for the brand theme. Pure function; the end-to-end
 * propagation into PDF/XLSX bytes is verified live.
 */
class BrandThemeServiceTest {

    @Test
    void parseHex_valid_withAndWithoutHash() {
        assertThat(BrandThemeService.parseHex("#094242", Color.BLACK)).isEqualTo(new Color(0x09, 0x42, 0x42));
        assertThat(BrandThemeService.parseHex("C9A977", Color.BLACK)).isEqualTo(new Color(0xC9, 0xA9, 0x77));
        assertThat(BrandThemeService.parseHex("#FFFFFF", Color.BLACK)).isEqualTo(Color.WHITE);
    }

    @Test
    void parseHex_invalid_returnsFallback() {
        Color fb = new Color(1, 2, 3);
        assertThat(BrandThemeService.parseHex(null, fb)).isSameAs(fb);
        assertThat(BrandThemeService.parseHex("", fb)).isSameAs(fb);
        assertThat(BrandThemeService.parseHex("#12345", fb)).isSameAs(fb);   // 5 hex digits
        assertThat(BrandThemeService.parseHex("#GGGGGG", fb)).isSameAs(fb);  // non-hex chars
        assertThat(BrandThemeService.parseHex("teal", fb)).isSameAs(fb);
    }

    @Test
    void defaults_matchPdfHousePalette() {
        // Guardrail: the brand defaults must equal PdfFonts.BRAND_PRIMARY/ACCENT so an
        // un-customised tenant renders exactly as before this feature.
        assertThat(BrandThemeService.parseHex(BrandThemeService.DEFAULT_PRIMARY_HEX, Color.BLACK))
            .isEqualTo(new Color(0x09, 0x42, 0x42));
        assertThat(BrandThemeService.parseHex(BrandThemeService.DEFAULT_ACCENT_HEX, Color.BLACK))
            .isEqualTo(new Color(0xC9, 0xA9, 0x77));
    }
}
