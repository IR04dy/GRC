package com.tss.grc.module.platform.export;

import com.tss.grc.common.exception.ExportException;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Export P5 — renders client-supplied tabular data ({@link TabularExportRequest}) into a branded
 * {@code .xlsx}. Reuses {@link XlsxStyler} (brand header + freeze + auto-filter + RTL + status
 * conditional-formatting) and the tenant {@link BrandThemeService} colour, so a client-side export
 * (e.g. a regulatory packet) becomes a real, house-styled workbook instead of a print-only PDF.
 */
@Service
@RequiredArgsConstructor
public class TabularExportService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TabularExportService.class);

    private final BrandThemeService brandThemeService;

    // ── resource-exhaustion guards (client supplies the whole payload) ──────────
    /** Per-cell character cap — below Excel's 32,767 hard limit (POI throws above it). */
    private static final int MAX_CELL_LEN = 32_000;
    /** Column cap per sheet (Excel allows 16,384; a regulator table needs far fewer). */
    private static final int MAX_COLS = 256;
    /** Total cell budget across the whole workbook/doc — bounds in-memory build + response size. */
    private static final long MAX_CELLS = 500_000L;

    /** Null-safe + length-capped cell value (never a formula — literal string). */
    private static String clip(String v) {
        if (v == null) return "";
        return v.length() > MAX_CELL_LEN ? v.substring(0, MAX_CELL_LEN) : v;
    }

    public byte[] xlsx(TabularExportRequest req) {
        boolean ar = "ar".equalsIgnoreCase(req.lang());
        Color brand = brandThemeService.resolve().primaryColor();

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = XlsxStyler.headerStyle(wb, brand);
            Set<String> used = new HashSet<>();
            long cells = 0;
            boolean budgetHit = false;

            for (TabularExportRequest.Sheet s : req.sheets()) {
                if (budgetHit) break;
                Sheet sheet = wb.createSheet(uniqueSheetName(s.name(), used));
                XlsxStyler.rightToLeft(sheet, ar);

                List<String> headers = s.headers() == null ? List.of() : s.headers();
                int nCols = Math.min(headers.size(), MAX_COLS);
                int r = 0;
                Row header = sheet.createRow(r++);
                for (int c = 0; c < nCols; c++) {
                    Cell cell = header.createCell(c);
                    cell.setCellValue(clip(headers.get(c)));   // length-capped literal (no formula)
                    cell.setCellStyle(headerStyle);
                }
                cells += nCols;

                List<List<String>> rows = s.rows() == null ? List.of() : s.rows();
                for (List<String> row : rows) {
                    if (cells >= MAX_CELLS) { budgetHit = true; break; }   // bound total in-memory size
                    Row dr = sheet.createRow(r++);
                    int cols = Math.min(row.size(), MAX_COLS);
                    for (int c = 0; c < cols; c++) {
                        dr.createCell(c).setCellValue(clip(row.get(c)));
                    }
                    cells += cols;
                }

                XlsxStyler.freezeAndFilter(sheet, nCols, r - 1);
                for (int c = 0; c < nCols; c++) {
                    if (XlsxStyler.isStatusHeader(headers.get(c))) {
                        XlsxStyler.statusColors(sheet, c, 1, r - 1);
                    }
                }
                XlsxStyler.autoSize(sheet, nCols);
            }
            if (budgetHit) log.warn("Tabular XLSX truncated at the {}-cell budget (module={}, type={})",
                MAX_CELLS, req.module(), req.exportType());

            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ExportException("XLSX", "Tabular XLSX generation failed", e);
        }
    }

    /**
     * Renders the same {@link TabularExportRequest} as a branded Word document — a title, then one
     * heading + table per sheet — reusing {@link DocxKit}'s XWPF helpers with the tenant
     * brand colour. RTL (bidi) when {@code lang=ar}.
     */
    public byte[] docx(TabularExportRequest req) {
        boolean ar = "ar".equalsIgnoreCase(req.lang());
        String hex = brandThemeService.resolve().primaryHex().replace("#", "");
        XWPFDocument doc = new XWPFDocument();
        try {
            XWPFParagraph title = doc.createParagraph();
            DocxKit.applyBidi(title, ar);
            title.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            XWPFRun tr = title.createRun();
            tr.setText(req.exportType());
            DocxKit.applyRtlRun(tr, ar);
            tr.setBold(true);
            tr.setFontSize(18);
            tr.setColor(hex);

            long cells = 0;
            boolean budgetHit = false;
            for (TabularExportRequest.Sheet s : req.sheets()) {
                if (budgetHit) break;
                XWPFParagraph head = doc.createParagraph();
                head.setSpacingBefore(240);
                DocxKit.applyBidi(head, ar);
                head.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
                XWPFRun hr = head.createRun();
                hr.setText(s.name());
                DocxKit.applyRtlRun(hr, ar);
                hr.setBold(true);
                hr.setFontSize(13);
                hr.setColor(hex);

                List<String> headersRaw = s.headers() == null ? List.of() : s.headers();
                if (headersRaw.isEmpty()) continue;
                int nCols = Math.min(headersRaw.size(), MAX_COLS);
                String[] headers = new String[nCols];
                for (int c = 0; c < nCols; c++) headers[c] = clip(headersRaw.get(c));
                XWPFTable table = DocxKit.newTable(doc, headers, ar, hex);
                cells += nCols;

                List<List<String>> rows = s.rows() == null ? List.of() : s.rows();
                for (List<String> row : rows) {
                    if (cells >= MAX_CELLS) { budgetHit = true; break; }
                    XWPFTableRow tRow = table.createRow();
                    for (int c = 0; c < nCols; c++) {
                        DocxKit.setCell(tRow, c, clip(c < row.size() ? row.get(c) : ""), ar);
                    }
                    cells += nCols;
                }
            }
            if (budgetHit) log.warn("Tabular DOCX truncated at the {}-cell budget (module={}, type={})",
                MAX_CELLS, req.module(), req.exportType());
            return DocxKit.bytes(doc);
        } finally {
            try { doc.close(); } catch (IOException ignored) { /* best-effort */ }
        }
    }

    /** Excel sheet names must be ≤31 chars, exclude {@code []:*?/\}, and be unique in the workbook. */
    static String uniqueSheetName(String raw, Set<String> used) {
        String base = (raw == null || raw.isBlank() ? "Sheet" : raw).replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (base.length() > 31) base = base.substring(0, 31);
        String name = base.isEmpty() ? "Sheet" : base;
        int n = 2;
        while (!used.add(name.toLowerCase())) {
            String suffix = " (" + n++ + ")";
            String trimmed = base.length() + suffix.length() > 31 ? base.substring(0, 31 - suffix.length()) : base;
            name = trimmed + suffix;
        }
        return name;
    }
}
