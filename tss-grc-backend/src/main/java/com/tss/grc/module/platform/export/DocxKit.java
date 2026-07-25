package com.tss.grc.module.platform.export;

import com.tss.grc.common.exception.ExportException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;

import java.io.ByteArrayOutputStream;

/**
 * Word (DOCX) toolkit — <b>kernel</b>, no business semantics: the RTL primitives plus the
 * generic table/cell/serialise helpers every DOCX exporter needs.
 *
 * <p><b>Why this class exists (F2-08a).</b> The export toolkit moved into the kernel, but
 * {@code TabularExportService} — one of the moved classes — called these helpers on
 * {@code DocxExportService} as a <em>same-package neighbour</em>, with no import. That is a
 * dependency <b>import analysis cannot see</b>, so the "the toolkit is self-contained"
 * check missed it and the move broke the build. Worth remembering: in Java, measuring
 * coupling by imports systematically under-counts intra-package coupling.
 *
 * <p>Moving {@code DocxExportService} itself was not an option — it imports
 * {@code change_mgmt}, {@code compliance} and {@code risk} entities and repositories, so it
 * is a business exporter and part of the separate F2-08b god-consumer problem. The honest
 * split is the one made here: the generic POI primitives are kernel, the exporter that
 * composes them into a risk register is not.
 *
 * <p>Every method is a no-op when {@code rtl} is false, so callers pass the locale flag
 * unconditionally. <b>Rule:</b> nothing in this package may import a business module.
 */
public final class DocxKit {

    private DocxKit() {}

    // ── RTL primitives ──────────────────────────────────────────────────────
    // Setting these three OOXML properties is what makes Arabic Word output correct
    // rather than merely right-aligned. The comments below record actual shipped bugs.

    /** Toggle RTL on a paragraph by setting the {@code w:bidi} run property. */
    public static void applyBidi(XWPFParagraph p, boolean rtl) {
        if (!rtl) return;
        if (p.getCTP().getPPr() == null) p.getCTP().addNewPPr();
        if (p.getCTP().getPPr().getBidi() == null) p.getCTP().getPPr().addNewBidi();
    }

    /**
     * {@code w:bidiVisual} on the table properties — without it Word lays the
     * columns out left-to-right even when every paragraph is bidi/right-aligned,
     * which is exactly how the Arabic register tables shipped ("tables start
     * from the left"). With it, Word mirrors the column order for RTL reading.
     */
    public static void applyBidiVisual(XWPFTable t, boolean rtl) {
        if (!rtl) return;
        CTTblPr pr = t.getCTTbl().getTblPr() != null ? t.getCTTbl().getTblPr() : t.getCTTbl().addNewTblPr();
        if (!pr.isSetBidiVisual()) pr.addNewBidiVisual();
    }

    /** {@code w:rtl} on the run properties — Word needs it to order mixed
     *  Arabic/Latin/digit runs correctly inside a bidi paragraph. */
    public static void applyRtlRun(XWPFRun r, boolean rtl) {
        if (!rtl) return;
        CTRPr pr = r.getCTR().isSetRPr() ? r.getCTR().getRPr() : r.getCTR().addNewRPr();
        if (pr.sizeOfRtlArray() == 0) pr.addNewRtl();
    }

    // ── generic table + serialisation helpers ───────────────────────────────

    /** Header-styled table using the platform's default teal header fill. */
    public static XWPFTable newTable(XWPFDocument doc, String[] headers, boolean ar) {
        return newTable(doc, headers, ar, "094242");
    }

    /** As {@link #newTable(XWPFDocument, String[], boolean)} but with a caller-supplied header
     *  fill colour (hex, no {@code #}) — lets the tabular exporter apply the tenant BrandTheme. */
    public static XWPFTable newTable(XWPFDocument doc, String[] headers, boolean ar, String headerHex) {
        XWPFTable t = doc.createTable(1, headers.length);
        t.setWidth("100%");
        applyBidiVisual(t, ar);   // mirror the column order for Arabic
        XWPFTableRow hr = t.getRow(0);
        for (int i = 0; i < headers.length; i++) {
            XWPFTableCell c = hr.getCell(i);
            c.setColor(headerHex);
            c.removeParagraph(0);
            XWPFParagraph p = c.addParagraph();
            applyBidi(p, ar);
            p.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
            XWPFRun r = p.createRun();
            r.setText(headers[i]);
            applyRtlRun(r, ar);
            r.setColor("FFFFFF");
            r.setBold(true);
            r.setFontSize(10);
        }
        return t;
    }

    /** Write one body cell with the correct direction and house text styling. */
    public static void setCell(XWPFTableRow row, int col, String text, boolean ar) {
        XWPFTableCell c = row.getCell(col);
        c.removeParagraph(0);
        XWPFParagraph p = c.addParagraph();
        applyBidi(p, ar);
        p.setAlignment(ar ? ParagraphAlignment.RIGHT : ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setText(text);
        applyRtlRun(r, ar);
        r.setColor("1A1A1A");
        r.setFontSize(9);
    }

    /** Serialise, translating any POI failure into the platform's export error. */
    public static byte[] bytes(XWPFDocument doc) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ExportException("Word", "Word write failed", e);
        }
    }
}
