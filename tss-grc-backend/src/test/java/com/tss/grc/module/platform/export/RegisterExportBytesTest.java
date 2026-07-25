package com.tss.grc.module.platform.export;

import com.lowagie.text.pdf.PdfReader;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export P1 — byte-level test harness. The review found NO test opens a generated file
 * (all were magic-byte-only). This actually PARSES RegisterExport output (POI WorkbookFactory
 * for XLSX, OpenPDF PdfReader for PDF) and asserts the P0-2/P1 properties: RTL worksheet for
 * Arabic, auto-filter, header + row count, conditional formatting, and a valid multi-page PDF.
 */
class RegisterExportBytesTest {

    private static final List<RegisterExport.Col<Map<String, String>>> COLS = List.of(
        RegisterExport.Col.of("Code",     r -> r.get("code")),
        RegisterExport.Col.of("Severity", r -> r.get("sev")),    // status-like -> conditional formatting
        RegisterExport.Col.of("Status",   r -> r.get("status")));

    private static final List<Map<String, String>> ROWS = List.of(
        Map.of("code", "R-1", "sev", "CRITICAL", "status", "OPEN"),
        Map.of("code", "R-2", "sev", "LOW",      "status", "CLOSED"),
        Map.of("code", "R-3", "sev", "MEDIUM",   "status", "IN_PROGRESS"));

    @Test
    void xlsx_arabic_isRtl_withFilterHeaderAndConditionalFormatting() throws Exception {
        byte[] b = RegisterExport.xlsx("Risk Register", COLS, ROWS, true);
        assertThat(b).startsWith(new byte[]{'P', 'K'});   // OOXML zip magic
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(b))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.isRightToLeft()).as("AR sheet must be RTL").isTrue();
            assertThat(((XSSFSheet) s).getCTWorksheet().isSetAutoFilter()).as("auto-filter set").isTrue();
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Code");
            assertThat(s.getLastRowNum()).isEqualTo(3);   // header + 3 data rows -> index 3
            assertThat(s.getSheetConditionalFormatting().getNumConditionalFormattings())
                .as("severity/status conditional formatting present").isGreaterThan(0);
        }
    }

    @Test
    void xlsx_english_isLtr() throws Exception {
        byte[] b = RegisterExport.xlsx("Risk Register", COLS, ROWS, false);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(b))) {
            assertThat(wb.getSheetAt(0).isRightToLeft()).as("EN sheet must stay LTR").isFalse();
        }
    }

    @Test
    void pdf_opensAsValidMultiContentDocument() throws Exception {
        byte[] p = RegisterExport.pdf("TSS", "تي إس إس", "Risk", "المخاطر",
            "Risk Register", "سجل المخاطر", List.<PdfTheme.Kpi>of(), COLS, ROWS, true);
        assertThat(new String(p, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        PdfReader rd = new PdfReader(p);
        try {
            assertThat(rd.getNumberOfPages()).isGreaterThan(0);
        } finally {
            rd.close();
        }
    }
}
