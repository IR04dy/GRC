package com.tss.grc.module.reporting.service;

import com.tss.grc.module.platform.export.DocxKit;

import com.tss.grc.module.platform.export.PdfTheme;

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

    @Test
    void visualStart_compensatesForMirroredAlignmentUnderRtlRuns() {
        // Measured live: ALIGN_RIGHT + RUN_DIRECTION_RTL rendered at the physical
        // LEFT (x=0.04) — OpenPDF mirrors LEFT/RIGHT semantics under RTL runs.
        int RTL = com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_RTL;
        int DEF = com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_DEFAULT;
        int LTR = com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_LTR;
        // Arabic doc, RTL run: reading start = visual right = mirrored ALIGN_LEFT.
        assertThat(PdfTheme.visualStart(true, RTL)).isEqualTo(com.lowagie.text.Element.ALIGN_LEFT);
        // English doc, no run direction: plain left.
        assertThat(PdfTheme.visualStart(false, DEF)).isEqualTo(com.lowagie.text.Element.ALIGN_LEFT);
        // Arabic line inside an English doc (RTL run): keep it at the visual left.
        assertThat(PdfTheme.visualStart(false, RTL)).isEqualTo(com.lowagie.text.Element.ALIGN_RIGHT);
        // English doc, LTR run (mixed line): no mirroring, plain left.
        assertThat(PdfTheme.visualStart(false, LTR)).isEqualTo(com.lowagie.text.Element.ALIGN_LEFT);
    }

    // ── DOCX table direction + run rtl ───────────────────────────────────

    @Test
    void arabicDocxTable_carriesBidiVisual_andRtlRuns() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable t = DocxKit.newTable(doc, new String[]{"الرقم", "المخاطرة"}, true);
            XWPFTableRow row = t.createRow();
            DocxKit.setCell(row, 0, "R-00001", true);
            DocxKit.setCell(row, 1, "مخاطرة تجريبية", true);

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
            XWPFTable t = DocxKit.newTable(doc, new String[]{"Code", "Risk"}, false);
            XWPFTableRow row = t.createRow();
            DocxKit.setCell(row, 0, "R-00001", false);

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
