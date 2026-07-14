package com.tss.grc.module.admin.committee.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeFormTemplate;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Form-template PDF exporter — renders any of the 10 V156 bilingual form
 * templates (CHARTER, AGENDA, MINUTES, MOTION, ATTENDANCE, ACTION_REGISTER,
 * COI_DECLARATION, SKILLS_MATRIX, ANNUAL_EVALUATION, PRE_READ_COVERSHEET)
 * as a branded PDF with the organisation's name in the cover band.
 *
 * <p>The Saudi-native Arabic markdown content of each template is rendered
 * inline with the English version so a regulator opening the PDF sees both
 * languages. The Arabic glyphs come through {@link PdfFonts#baseFont()}
 * (Noto-Naskh or fallback) so right-to-left shaping happens at the font
 * layer, not the document direction.
 *
 * <p>Forms are <em>blank templates</em> — bracketed placeholders like
 * {@code [COMMITTEE NAME]} stay in the output so a Secretary downloads the
 * PDF and fills it in offline. A future enhancement could accept a values
 * map to interpolate placeholders, but blank-template export is the most
 * common case (printed and used in physical meetings).
 */
@Service
@RequiredArgsConstructor
public class CommitteeFormExportService {

    private final CommitteeCatalogService    catalog;
    private final OrganisationRepository     orgs;
    private final AuditLogService            audit;

    @Transactional(readOnly = true)
    public byte[] exportPdf(String formCode) {
        return exportPdf(formCode, "en");
    }

    /** @param lang pass "ar" to flip the document to RTL run direction (P3).
     *  Default "en" leaves output byte-identical to the pre-lang behaviour. */
    @Transactional(readOnly = true)
    public byte[] exportPdf(String formCode, String lang) {
        boolean ar = "ar".equalsIgnoreCase(lang);
        int rd = PdfTheme.runDir(ar);
        CommitteeFormTemplate form = catalog.getForm(formCode);
        Long orgId = TenantContext.getCurrentTenant();
        Organisation org = orgs.findById(orgId).orElse(null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(rd);
            String docLabel = "Form · " + form.getFormCode() + " · v" + form.getVersion();
            writer.setPageEvent(PdfTheme.professionalFooter(docLabel, "Internal"));
            doc.open();

            renderCover(doc, form, org);
            renderPurpose(doc, form);
            renderContent(doc, form);
            renderCompletionGuide(doc, form);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "Form PDF generation failed", e);
        } catch (EntityNotFoundException nf) {
            throw nf;
        }

        audit.log("EXPORT_PDF", "CommitteeFormTemplate", form.getId(),
            "PDF exported for form " + form.getFormCode() + " v" + form.getVersion());

        return baos.toByteArray();
    }

    // ─── Cover ─────────────────────────────────────────────────────────

    private void renderCover(Document doc, CommitteeFormTemplate form, Organisation org)
            throws DocumentException {
        PdfTheme.Cover cv = new PdfTheme.Cover();
        cv.eyebrow     = "Committee Form · " + form.getFormType().replace('_', ' ');
        cv.eyebrowAr   = "نموذج لجنة · " + arabicFormType(form.getFormType());
        cv.code        = form.getFormCode();
        cv.titleEn     = nz(form.getTitleEn());
        cv.titleAr     = form.getTitleAr();
        cv.orgNameEn   = org == null ? null : org.getNameEn();
        cv.orgNameAr   = org == null ? null : org.getNameAr();
        cv.classification = "Internal";
        PdfTheme.coverHero(doc, cv);

        // Metadata grid — applicable scope, regulator anchors, version.
        PdfPTable meta = PdfTheme.metadataGrid();
        PdfTheme.addMetaCell(meta, "Form code",         form.getFormCode());
        PdfTheme.addMetaCell(meta, "Form type",         form.getFormType().replace('_', ' '));
        PdfTheme.addMetaCell(meta, "Version",           "v" + nz(form.getVersion()));
        PdfTheme.addMetaCell(meta, "Applicable to",     nz(form.getApplicableTo()));
        PdfTheme.addMetaCell(meta, "Regulator anchors", nz(form.getRegulatorAnchors()));
        PdfTheme.addMetaCell(meta, "Generated",         LocalDate.now().toString());
        doc.add(meta);
    }

    // ─── Purpose ───────────────────────────────────────────────────────

    private void renderPurpose(Document doc, CommitteeFormTemplate form) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Purpose", "الغرض");

        Paragraph pEn = new Paragraph(nz(form.getPurposeEn()), PdfFonts.normal(10));
        pEn.setSpacingAfter(4);
        doc.add(pEn);

        if (form.getPurposeAr() != null && !form.getPurposeAr().isBlank()) {
            // Shaped + right-aligned — a plain Paragraph renders the Arabic
            // purpose disconnected and anchored left.
            PdfTheme.line(doc, form.getPurposeAr(), PdfFonts.normal(10), true, 0, 10);
        }
    }

    // ─── Content (the actual fillable form body) ───────────────────────

    private void renderContent(Document doc, CommitteeFormTemplate form) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Form Body (English)", "نص النموذج بالإنجليزية");
        for (Paragraph p : markdownToParagraphs(nz(form.getContentMdEn()))) {
            doc.add(p);
        }

        if (form.getContentMdAr() != null && !form.getContentMdAr().isBlank()) {
            doc.newPage();
            PdfTheme.sectionDivider(doc, "Form Body (Arabic)", "نص النموذج بالعربية");
            renderMarkdownShaped(doc, form.getContentMdAr());
        }
    }

    // ─── Completion guide ──────────────────────────────────────────────

    private void renderCompletionGuide(Document doc, CommitteeFormTemplate form) throws DocumentException {
        boolean hasEn = form.getCompletionGuideEn() != null && !form.getCompletionGuideEn().isBlank();
        boolean hasAr = form.getCompletionGuideAr() != null && !form.getCompletionGuideAr().isBlank();
        if (!hasEn && !hasAr) return;

        doc.newPage();
        PdfTheme.sectionDivider(doc, "Completion Guide", "دليل التعبئة");

        if (hasEn) {
            for (Paragraph p : markdownToParagraphs(form.getCompletionGuideEn())) {
                doc.add(p);
            }
        }
        if (hasAr) {
            Paragraph sp = new Paragraph(" ", PdfFonts.small(8));
            sp.setSpacingAfter(8);
            doc.add(sp);
            renderMarkdownShaped(doc, form.getCompletionGuideAr());
        }
    }

    /**
     * Arabic twin of {@link #markdownToParagraphs}: same mini-markdown rules,
     * but every line goes through {@link PdfTheme#line} so the Arabic shapes
     * and starts at the RIGHT (plain Paragraphs render it disconnected/left).
     */
    private static void renderMarkdownShaped(Document doc, String md) throws DocumentException {
        if (md == null || md.isBlank()) return;
        for (String raw : md.split("\\r?\\n")) {
            String line = raw == null ? "" : raw.replace('\t', ' ');
            if (line.isBlank()) {
                Paragraph p = new Paragraph(" ", PdfFonts.normal(10));
                p.setSpacingAfter(2);
                doc.add(p);
            } else if (line.startsWith("### ")) {
                PdfTheme.line(doc, line.substring(4).trim(), PdfFonts.bold(11), true, 6, 2);
            } else if (line.startsWith("## ")) {
                PdfTheme.line(doc, line.substring(3).trim(), PdfFonts.bold(12), true, 8, 3);
            } else if (line.startsWith("# ")) {
                PdfTheme.line(doc, line.substring(2).trim(), PdfFonts.bold(14), true, 10, 4);
            } else if (line.startsWith("- ")) {
                PdfTheme.line(doc, "•  " + line.substring(2).trim(), PdfFonts.normal(10), true, 0, 1);
            } else {
                PdfTheme.line(doc, line, PdfFonts.normal(10), true, 0, 2);
            }
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────

    /**
     * Very-lightweight markdown → PDF paragraphs. Recognises:
     * <ul>
     *   <li>{@code # / ## / ###} as headings (sized 14/12/11pt bold)</li>
     *   <li>{@code - } as bulleted items (rendered with "• " prefix)</li>
     *   <li>blank line as paragraph break</li>
     *   <li>everything else as body copy</li>
     * </ul>
     * <p>No bold/italic inline markup — keeps the parser robust and lets the
     * Saudi-native Arabic content render predictably.
     */
    private static List<Paragraph> markdownToParagraphs(String md) {
        java.util.List<Paragraph> out = new java.util.ArrayList<>();
        if (md == null || md.isBlank()) return out;
        for (String raw : md.split("\\r?\\n")) {
            String line = raw == null ? "" : raw.replace('\t', ' ');
            if (line.isBlank()) {
                Paragraph p = new Paragraph(" ", PdfFonts.normal(10));
                p.setSpacingAfter(2);
                out.add(p);
                continue;
            }
            if (line.startsWith("### ")) {
                Paragraph p = new Paragraph(line.substring(4).trim(), PdfFonts.bold(11));
                p.setSpacingBefore(6); p.setSpacingAfter(2);
                out.add(p);
            } else if (line.startsWith("## ")) {
                Paragraph p = new Paragraph(line.substring(3).trim(), PdfFonts.bold(12));
                p.setSpacingBefore(8); p.setSpacingAfter(3);
                out.add(p);
            } else if (line.startsWith("# ")) {
                Paragraph p = new Paragraph(line.substring(2).trim(), PdfFonts.bold(14));
                p.setSpacingBefore(10); p.setSpacingAfter(4);
                out.add(p);
            } else if (line.startsWith("- ")) {
                Paragraph p = new Paragraph("•  " + line.substring(2).trim(), PdfFonts.normal(10));
                p.setIndentationLeft(12);
                p.setSpacingAfter(1);
                out.add(p);
            } else {
                Paragraph p = new Paragraph(line, PdfFonts.normal(10));
                p.setSpacingAfter(2);
                out.add(p);
            }
        }
        return out;
    }

    private static String arabicFormType(String formType) {
        return switch (formType) {
            case "CHARTER"             -> "ميثاق";
            case "AGENDA"              -> "جدول أعمال";
            case "PRE_READ_COVERSHEET" -> "ورقة غلاف القراءة المسبقة";
            case "ATTENDANCE"          -> "الحضور";
            case "MOTION"              -> "مقترح/قرار";
            case "MINUTES"             -> "محضر";
            case "ACTION_REGISTER"     -> "سجل المهام";
            case "COI_DECLARATION"     -> "إقرار تضارب المصالح";
            case "SKILLS_MATRIX"       -> "مصفوفة المهارات";
            case "ANNUAL_EVALUATION"   -> "التقييم السنوي";
            default                     -> formType;
        };
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
