package com.tss.grc.module.reporting.service;

// Explicit imports — wildcard com.lowagie.text.* collides with
// org.apache.poi.ss.usermodel.* on Cell / Row / Font.
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * CY-F — reusable, column-driven register-export engine.
 *
 * <p>Given a column model (a header + a {@code Function<row,String>} extractor)
 * and a row list it emits either:
 * <ul>
 *   <li><b>XLSX</b> — one styled sheet: dark-teal header row, frozen header,
 *       auto-sized columns (Apache POI).</li>
 *   <li><b>PDF</b> — the shared executive theme (cover hero → KPI strip →
 *       branded table), A4 landscape, Arabic-capable via {@link PdfFonts}
 *       (bundled Noto/Arial, IDENTITY_H) so RTL glyphs render.</li>
 * </ul>
 *
 * <p>The cyber and SOC-Ops register exporters both call this, so the whole
 * platform prints in one house style without each module re-implementing the
 * POI / OpenPDF plumbing — mirrors {@link ExportService} / {@link PdfTheme}.
 */
public final class RegisterExport {

    private RegisterExport() {}

    /** One column: header text + how to render a row's cell value. */
    public record Col<T>(String header, Function<T, String> get) {
        public static <T> Col<T> of(String header, Function<T, String> get) {
            return new Col<>(header, get);
        }
    }

    // ── XLSX ─────────────────────────────────────────────────────────────

    public static <T> byte[] xlsx(String sheetName, List<Col<T>> cols, List<T> rows) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(safeSheet(sheetName));
            CellStyle hdrStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            hdrStyle.setFont(f);
            hdrStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            hdrStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hdrStyle.setAlignment(HorizontalAlignment.LEFT);

            Row h = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols.get(i).header());
                c.setCellStyle(hdrStyle);
            }
            int r = 1;
            for (T row : rows) {
                Row dr = sheet.createRow(r++);
                for (int i = 0; i < cols.size(); i++) {
                    dr.createCell(i).setCellValue(nz(cols.get(i).get().apply(row)));
                }
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < cols.size(); i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportException("Excel", "Register XLSX export failed", e);
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────

    public static <T> byte[] pdf(String orgNameEn, String orgNameAr,
                                 String eyebrowEn, String eyebrowAr,
                                 String titleEn, String titleAr,
                                 List<PdfTheme.Kpi> kpis, List<Col<T>> cols, List<T> rows, boolean ar) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            // P0-2: RTL layout for Arabic register PDFs (cyber + SOC-Ops) — was LTR-only.
            writer.setRunDirection(PdfTheme.runDir(ar));
            writer.setPageEvent(PdfTheme.professionalFooter(eyebrowEn, "Confidential"));
            doc.open();

            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = eyebrowEn;
            cv.eyebrowAr = eyebrowAr;
            cv.titleEn = titleEn;
            cv.titleAr = titleAr;
            cv.orgNameEn = orgNameEn;
            cv.orgNameAr = orgNameAr;
            cv.classification = "Confidential";
            cv.generatedAt = LocalDateTime.now();
            PdfTheme.coverHero(doc, cv);

            if (kpis != null && !kpis.isEmpty()) PdfTheme.kpiStrip(doc, new ArrayList<>(kpis));

            PdfTheme.sectionDivider(doc, titleEn + " (" + rows.size() + ")", titleAr);

            PdfPTable t = new PdfPTable(cols.size());
            t.setWidthPercentage(100);
            t.setRunDirection(PdfTheme.runDir(ar));
            for (Col<T> c : cols) t.addCell(headerCell(c.header(), ar));
            for (T row : rows) {
                for (Col<T> c : cols) cell(t, c.get().apply(row));
            }
            doc.add(t);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ExportException("PDF", "Register PDF export failed", e);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static PdfPCell headerCell(String text, boolean ar) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(8.5f)));
        c.setHorizontalAlignment(PdfTheme.alignStart(ar));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(5); c.setPaddingBottom(5); c.setPaddingLeft(5); c.setPaddingRight(5);
        return c;
    }

    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text == null || text.isBlank() ? "—" : text, PdfFonts.body(8)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(4); c.setPaddingBottom(4); c.setPaddingLeft(5); c.setPaddingRight(5);
        t.addCell(c);
    }

    private static String nz(String v) { return v == null ? "" : v; }

    /** Excel sheet names cannot exceed 31 chars or contain \ / ? * [ ] :. */
    private static String safeSheet(String s) {
        if (s == null || s.isBlank()) return "Export";
        String x = s.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return x.length() > 31 ? x.substring(0, 31) : x;
    }
}
