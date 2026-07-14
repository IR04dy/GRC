package com.tss.grc.module.reporting.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arabic export RTL fix — pins the two defects reported from live use:
 * <ul>
 *   <li><b>PDF</b>: Arabic rendered as disconnected letters (plain paragraphs
 *       skip OpenPDF's bidi shaper) — {@link PdfTheme#hasArabic} /
 *       {@link PdfTheme#firstStrongIsArabic} route Arabic-bearing text through
 *       run-direction-aware cells.</li>
 *   <li><b>Word</b>: Arabic tables laid out left-to-right — {@code w:bidiVisual}
 *       was never set on the table properties, and runs lacked {@code w:rtl}.</li>
 * </ul>
 */
class ArabicExportRtlTest {

    // ── PDF routing predicates ───────────────────────────────────────────

    @Test
    void hasArabic_detectsArabicAcrossBlocks_andIgnoresLatin() {
        assertThat(PdfTheme.hasArabic("سجل المخاطر")).isTrue();
        assertThat(PdfTheme.hasArabic("Report — سرّي")).isTrue();
        assertThat(PdfTheme.hasArabic("Risk Register 2026")).isFalse();
        assertThat(PdfTheme.hasArabic(null)).isFalse();
    }

    @Test
    void firstStrongIsArabic_classifiesLineDirectionByLeadingLetter() {
        assertThat(PdfTheme.firstStrongIsArabic("سجل المخاطر (Top 1000)")).isTrue();
        assertThat(PdfTheme.firstStrongIsArabic("2026 — تقرير")).isTrue();      // digits skipped
        assertThat(PdfTheme.firstStrongIsArabic("Generated هـ 1448")).isFalse(); // Latin-leading
        assertThat(PdfTheme.firstStrongIsArabic("—")).isFalse();
    }

    // ── DOCX table direction + run rtl ───────────────────────────────────

    @Test
    void arabicDocxTable_carriesBidiVisual_andRtlRuns() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable t = DocxExportService.newTable(doc, new String[]{"الرقم", "المخاطرة"}, true);
            XWPFTableRow row = t.createRow();
            DocxExportService.setCell(row, 0, "R-00001", true);
            DocxExportService.setCell(row, 1, "مخاطرة تجريبية", true);

            // The debug serializer uses the main: prefix instead of w: — assert
            // on the local names so the test is prefix-agnostic.
            String xml = docXml(doc);
            assertThat(xml).contains("bidiVisual");                 // table columns mirror RTL
            assertThat(xml).contains(":rtl/>");                     // runs ordered for Arabic
            assertThat(xml).contains(":bidi/>");                    // paragraphs stay bidi
        }
    }

    @Test
    void englishDocxTable_staysLtr() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable t = DocxExportService.newTable(doc, new String[]{"Code", "Risk"}, false);
            XWPFTableRow row = t.createRow();
            DocxExportService.setCell(row, 0, "R-00001", false);

            String xml = docXml(doc);
            assertThat(xml).doesNotContain("bidiVisual");
            assertThat(xml).doesNotContain(":rtl/>");
        }
    }

    private static String docXml(XWPFDocument doc) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.getDocument().save(baos);
            return baos.toString("UTF-8");
        }
    }
}
