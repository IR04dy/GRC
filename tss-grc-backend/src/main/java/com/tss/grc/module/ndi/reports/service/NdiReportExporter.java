package com.tss.grc.module.ndi.reports.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.module.ndi.reports.service.NdiReportService.ReportResult;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binary exporters for NDI reports — slice 11 follow-up.
 *
 * <p>Takes the structured {@link ReportResult} the synthesizer already
 * produces and emits PDF or XLSX bytes. Generic format: one
 * "Summary" pane (key/value pairs) + one "Rows" table built from the
 * union of all keys present in {@code rows}.
 *
 * <p>Arabic in PDF: OpenPDF's built-in fonts don't ship Arabic glyphs.
 * Arabic columns in the row data still appear in the document; they
 * render as boxes unless an Arabic TTF is installed and registered.
 * XLSX has no such restriction — Excel handles RTL natively.
 */
@Component
public class NdiReportExporter {

    public byte[] toXlsx(ReportResult result) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font hf = wb.createFont();
            hf.setBold(true);
            headerStyle.setFont(hf);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Summary sheet ────────────────────────────────────────
            Sheet summary = wb.createSheet("Summary");
            Row h0 = summary.createRow(0);
            h0.createCell(0).setCellValue("Report type");
            h0.createCell(1).setCellValue(result.reportType());
            h0.getCell(0).setCellStyle(headerStyle);

            Row h1 = summary.createRow(1);
            h1.createCell(0).setCellValue("Generated at");
            h1.createCell(1).setCellValue(String.valueOf(result.generatedAt()));
            h1.getCell(0).setCellStyle(headerStyle);

            Row h2 = summary.createRow(2);
            h2.createCell(0).setCellValue("Cycle id");
            h2.createCell(1).setCellValue(String.valueOf(result.cycleId()));
            h2.getCell(0).setCellStyle(headerStyle);

            int row = 4;
            Row s0 = summary.createRow(row++);
            s0.createCell(0).setCellValue("Key");
            s0.createCell(1).setCellValue("Value");
            s0.getCell(0).setCellStyle(headerStyle);
            s0.getCell(1).setCellStyle(headerStyle);

            for (Map.Entry<String, Object> e : result.summary().entrySet()) {
                Row r = summary.createRow(row++);
                r.createCell(0).setCellValue(e.getKey());
                r.createCell(1).setCellValue(asString(e.getValue()));
            }
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            // ── Rows sheet ───────────────────────────────────────────
            if (!result.rows().isEmpty()) {
                Sheet rows = wb.createSheet("Rows");
                List<String> cols = collectColumns(result.rows());
                Row header = rows.createRow(0);
                for (int i = 0; i < cols.size(); i++) {
                    org.apache.poi.ss.usermodel.Cell c = header.createCell(i);
                    c.setCellValue(cols.get(i));
                    c.setCellStyle(headerStyle);
                }
                int r = 1;
                for (Map<String, Object> data : result.rows()) {
                    Row dataRow = rows.createRow(r++);
                    for (int i = 0; i < cols.size(); i++) {
                        Object v = data.get(cols.get(i));
                        dataRow.createCell(i).setCellValue(asString(v));
                    }
                }
                for (int i = 0; i < cols.size(); i++) rows.autoSizeColumn(i);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] toPdf(ReportResult result) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font hFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font cFont     = FontFactory.getFont(FontFactory.HELVETICA, 9);

            doc.add(new Paragraph(prettyTitle(result.reportType()), titleFont));
            doc.add(new Paragraph("Generated at: " + result.generatedAt(), cFont));
            doc.add(new Paragraph("Cycle: " + (result.cycleId() == null ? "n/a" : result.cycleId()), cFont));
            doc.add(new Paragraph(" "));

            // Summary table.
            if (!result.summary().isEmpty()) {
                doc.add(new Paragraph("Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                PdfPTable summary = new PdfPTable(2);
                summary.setWidthPercentage(60);
                summary.setHorizontalAlignment(Element.ALIGN_LEFT);
                summary.addCell(headerCell("Key", hFont));
                summary.addCell(headerCell("Value", hFont));
                for (Map.Entry<String, Object> e : result.summary().entrySet()) {
                    summary.addCell(bodyCell(e.getKey(), cFont));
                    summary.addCell(bodyCell(asString(e.getValue()), cFont));
                }
                doc.add(summary);
                doc.add(new Paragraph(" "));
            }

            // Rows table.
            if (!result.rows().isEmpty()) {
                doc.add(new Paragraph("Rows (" + result.rows().size() + ")",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                List<String> cols = collectColumns(result.rows());
                PdfPTable table = new PdfPTable(cols.size());
                table.setWidthPercentage(100);
                for (String c : cols) table.addCell(headerCell(c, hFont));
                for (Map<String, Object> data : result.rows()) {
                    for (String c : cols) table.addCell(bodyCell(asString(data.get(c)), cFont));
                }
                doc.add(table);
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("PDF export failed", ex);
        }
    }

    public byte[] toCsv(ReportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(result.reportType()).append('\n');
        sb.append("# generated_at,").append(result.generatedAt()).append('\n');
        if (result.cycleId() != null) sb.append("# cycle_id,").append(result.cycleId()).append('\n');
        if (!result.rows().isEmpty()) {
            List<String> cols = collectColumns(result.rows());
            sb.append(String.join(",", cols.stream().map(this::csvEscape).toList())).append('\n');
            for (Map<String, Object> data : result.rows()) {
                List<String> cells = new ArrayList<>();
                for (String c : cols) cells.add(csvEscape(asString(data.get(c))));
                sb.append(String.join(",", cells)).append('\n');
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static List<String> collectColumns(List<Map<String, Object>> rows) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) seen.addAll(r.keySet());
        return new ArrayList<>(seen);
    }

    private static String asString(Object v) {
        if (v == null) return "";
        return String.valueOf(v);
    }

    private static String prettyTitle(String reportType) {
        return "NDI Report — " + reportType.replace('_', ' ').toLowerCase();
    }

    private static PdfPCell headerCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBackgroundColor(new java.awt.Color(220, 220, 220));
        c.setPadding(4f);
        return c;
    }

    private static PdfPCell bodyCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(3f);
        return c;
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
