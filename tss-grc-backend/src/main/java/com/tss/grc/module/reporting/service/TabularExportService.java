package com.tss.grc.module.reporting.service;

import com.tss.grc.common.exception.ExportException;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.reporting.dto.TabularExportRequest;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

    private final BrandThemeService brandThemeService;

    public byte[] xlsx(TabularExportRequest req) {
        boolean ar = "ar".equalsIgnoreCase(req.lang());
        Color brand = brandThemeService.resolve().primaryColor();

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = XlsxStyler.headerStyle(wb, brand);
            Set<String> used = new HashSet<>();

            for (TabularExportRequest.Sheet s : req.sheets()) {
                Sheet sheet = wb.createSheet(uniqueSheetName(s.name(), used));
                XlsxStyler.rightToLeft(sheet, ar);

                List<String> headers = s.headers() == null ? List.of() : s.headers();
                int r = 0;
                Row header = sheet.createRow(r++);
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = header.createCell(c);
                    cell.setCellValue(headers.get(c) == null ? "" : headers.get(c));
                    cell.setCellStyle(headerStyle);
                }

                List<List<String>> rows = s.rows() == null ? List.of() : s.rows();
                for (List<String> row : rows) {
                    Row dr = sheet.createRow(r++);
                    for (int c = 0; c < row.size(); c++) {
                        // setCellValue writes a literal string — never a formula — so a leading
                        // '='/'+'/'-'/'@' cannot inject a formula (unlike CSV).
                        dr.createCell(c).setCellValue(row.get(c) == null ? "" : row.get(c));
                    }
                }

                int nCols = headers.size();
                XlsxStyler.freezeAndFilter(sheet, nCols, r - 1);
                for (int c = 0; c < nCols; c++) {
                    if (XlsxStyler.isStatusHeader(headers.get(c))) {
                        XlsxStyler.statusColors(sheet, c, 1, r - 1);
                    }
                }
                XlsxStyler.autoSize(sheet, nCols);
            }

            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ExportException("XLSX", "Tabular XLSX generation failed", e);
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
