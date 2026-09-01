package com.tss.grc.module.platform.export;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Locale;
import java.awt.Color;

/**
 * Export remediation P1 — one shared Apache-POI styling helper so every XLSX export gets the same
 * house treatment instead of four hand-rolled header styles. Adds the three things the review found
 * missing everywhere: <b>auto-filter</b>, <b>RTL worksheet direction</b> for Arabic, and
 * <b>severity/status conditional formatting</b> — plus the brand-teal frozen header and auto-sizing.
 *
 * <p>Stateless + static, mirrors {@link PdfTheme} for PDFs.
 */
public final class XlsxStyler {

    private XlsxStyler() {}

    /** Brand dark-teal header cell style (white bold) — matches the PDF house palette. */
    public static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }

    /**
     * Brand-coloured header cell style (white bold on the tenant's primary colour). Used to
     * propagate a per-tenant {@code BrandTheme} into workbooks. Falls back to the default
     * {@link #headerStyle(Workbook)} when no brand colour is supplied or the workbook is not
     * XSSF (the custom RGB fill needs an .xlsx/XSSF workbook).
     */
    public static CellStyle headerStyle(Workbook wb, Color brand) {
        if (brand == null || !(wb instanceof XSSFWorkbook)) return headerStyle(wb);
        XSSFCellStyle s = (XSSFCellStyle) wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(brand, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }

    /** Freeze the header row + enable an auto-filter over the whole populated range
     *  (header row 0 .. lastDataRow, columns 0 .. nCols-1). No-op if there are no columns. */
    public static void freezeAndFilter(Sheet sheet, int nCols, int lastDataRow) {
        if (nCols <= 0) return;
        sheet.createFreezePane(0, 1);
        int lastRow = Math.max(0, lastDataRow);
        sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, nCols - 1));
    }

    /** Right-to-left worksheet direction for Arabic exports (first column on the right). */
    public static void rightToLeft(Sheet sheet, boolean ar) {
        sheet.setRightToLeft(ar);
    }

    /** Auto-size every column (cheap for typical register sizes). */
    public static void autoSize(Sheet sheet, int nCols) {
        for (int i = 0; i < nCols; i++) sheet.autoSizeColumn(i);
    }

    /** True when a column header looks like a status/severity/RAG column worth colour-coding. */
    public static boolean isStatusHeader(String header) {
        if (header == null) return false;
        String h = header.toLowerCase(Locale.ROOT);
        return h.contains("status") || h.contains("severity") || h.contains("level")
            || h.contains("band") || h.contains("rag") || h.contains("priority")
            || h.contains("الحالة") || h.contains("الأهمية") || h.contains("المستوى");
    }

    /**
     * Traffic-light conditional formatting on a status/severity column: cells containing
     * CRITICAL/HIGH/RED/OVERDUE/BREACHED → red, MEDIUM/AMBER/WARNING/DUE → amber,
     * LOW/GREEN/CLOSED/RESOLVED/COMPLETED/ON_TRACK → green. Applies to data rows only.
     */
    public static void statusColors(Sheet sheet, int colIdx, int firstDataRow, int lastDataRow) {
        if (lastDataRow < firstDataRow) return;
        SheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();
        String cell = CellReference.convertNumToColString(colIdx) + (firstDataRow + 1); // relative anchor, 1-based
        ConditionalFormattingRule red   = fill(scf, containsAny(cell, "CRITICAL", "HIGH", "RED", "OVERDUE", "BREACHED"), IndexedColors.ROSE);
        ConditionalFormattingRule amber = fill(scf, containsAny(cell, "MEDIUM", "AMBER", "WARNING"),                    IndexedColors.LIGHT_ORANGE);
        ConditionalFormattingRule green = fill(scf, containsAny(cell, "LOW", "GREEN", "CLOSED", "RESOLVED", "COMPLETED", "ON_TRACK"), IndexedColors.LIGHT_GREEN);
        CellRangeAddress[] range = { new CellRangeAddress(firstDataRow, lastDataRow, colIdx, colIdx) };
        scf.addConditionalFormatting(range, new ConditionalFormattingRule[]{ red, amber, green });
    }

    private static ConditionalFormattingRule fill(SheetConditionalFormatting scf, String formula, IndexedColors color) {
        ConditionalFormattingRule rule = scf.createConditionalFormattingRule(formula);
        PatternFormatting pf = rule.createPatternFormatting();
        pf.setFillBackgroundColor(color.getIndex());
        pf.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        return rule;
    }

    /** Excel formula: TRUE when {@code cell} contains any of the (case-insensitive) tokens. */
    private static String containsAny(String cell, String... tokens) {
        StringBuilder sb = new StringBuilder("OR(");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("ISNUMBER(SEARCH(\"").append(tokens[i]).append("\",").append(cell).append("))");
        }
        return sb.append(')').toString();
    }
}
