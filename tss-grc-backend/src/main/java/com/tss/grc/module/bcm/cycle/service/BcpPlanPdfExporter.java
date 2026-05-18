package com.tss.grc.module.bcm.cycle.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.reporting.service.PdfFonts;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * BCP / DRP PDF exporter — audit Tier-2 #12.
 *
 * <p>Renders an approved/published plan to a single PDF that an auditor or
 * a recipient can read offline. Output layout:
 *
 * <ol>
 *   <li>Cover page — title, plan code, version, owner, recovery targets,
 *       last-attested stamp.</li>
 *   <li>Per-section pages — one section per ISO 22301 §8.4.2 named section,
 *       with EN body followed by AR body (when present).</li>
 *   <li>Version history table — every prior approval with approver + date
 *       + change summary.</li>
 *   <li>Distribution roster — who received which version + ack status.</li>
 * </ol>
 *
 * <p>Bilingual via the existing {@link PdfFonts} which loads an Arabic-capable
 * font on startup and falls back gracefully if none is available. The PDF
 * stays LTR throughout; Arabic blocks are rendered with the same font so
 * the right-to-left script direction comes from the font shaper, not the
 * document run direction (avoids flipping English titles + section keys).
 */
@Service
@RequiredArgsConstructor
public class BcpPlanPdfExporter {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BcpPlanRepository                       plans;
    private final BcpSectionRepository                    sections;
    private final BcpVersionRepository                    versions;
    private final BcpPlanAcknowledgementRepository        acks;
    private final BcpPlanReviewAttestationRepository      attestations;
    private final AuditLogService                         audit;

    @Transactional(readOnly = true)
    public byte[] export(Long planId) {
        Long org = TenantContext.getCurrentTenant();
        BcpPlan p = plans.findByIdAndOrganisationId(planId, org)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));

        List<BcpSection>          secs  = sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(org, planId);
        List<BcpVersion>          vers  = versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(org, planId);
        List<BcpPlanAcknowledgement> dist = acks.findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(org, planId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new PlanPageEvent(p.getPlanType() + " · " + p.getPlanCode()));
            doc.open();

            renderCover(doc, p);
            renderSections(doc, secs);
            renderVersionHistory(doc, vers);
            renderDistributionRoster(doc, dist);
            renderAttestationFooter(doc, p);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Plan PDF generation failed", e);
        }

        audit.log("EXPORT_PDF", "BcpPlan", planId,
            "PDF exported for " + p.getPlanCode() + " v" + p.getCurrentVersion());

        return baos.toByteArray();
    }

    // ─── Cover ─────────────────────────────────────────────────────────

    private void renderCover(Document doc, BcpPlan p) throws DocumentException {
        Paragraph eyebrow = new Paragraph(
            p.getPlanType().equals("BCP") ? "Business Continuity Plan" : "Disaster Recovery Plan",
            PdfFonts.small(10));
        eyebrow.setSpacingBefore(80);
        doc.add(eyebrow);

        Paragraph code = new Paragraph(p.getPlanCode(), PdfFonts.bold(20));
        code.setSpacingAfter(4);
        doc.add(code);

        Paragraph titleEn = new Paragraph(nz(p.getTitleEn()), PdfFonts.bold(16));
        titleEn.setSpacingAfter(2);
        doc.add(titleEn);

        if (p.getTitleAr() != null && !p.getTitleAr().isBlank()) {
            Paragraph titleAr = new Paragraph(p.getTitleAr(), PdfFonts.normal(14));
            titleAr.setSpacingAfter(20);
            doc.add(titleAr);
        }

        // Metadata grid as a 2-column borderless table
        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(20);
        try { meta.setWidths(new float[] {1, 2, 1, 2}); } catch (DocumentException ignored) {}

        addMeta(meta, "Status",         nz(p.getStatus()));
        addMeta(meta, "Version",        "v" + (p.getCurrentVersion() == null ? 1 : p.getCurrentVersion()));
        addMeta(meta, "Plan type",      nz(p.getPlanType()));
        addMeta(meta, "Source BIA",     p.getSourceBiaId() == null ? "—" : ("#" + p.getSourceBiaId()));

        addMeta(meta, "RTO",            p.getRtoHours()  == null ? "—" : (p.getRtoHours()  + "h"));
        addMeta(meta, "RPO",            p.getRpoHours()  == null ? "—" : (p.getRpoHours()  + "h"));
        addMeta(meta, "MTPD",           p.getMtpdHours() == null ? "—" : (p.getMtpdHours() + "h"));
        addMeta(meta, "Response team",  nz(p.getResponseTeamRole()));

        addMeta(meta, "Approved by",    nz(p.getApprovedBy()));
        addMeta(meta, "Approved at",    p.getApprovedAt()  == null ? "—" : p.getApprovedAt().format(DT));
        addMeta(meta, "Published at",   p.getPublishedAt() == null ? "—" : p.getPublishedAt().format(DT));
        addMeta(meta, "Next review",    p.getNextReviewDate() == null ? "—" : p.getNextReviewDate().format(DATE));

        addMeta(meta, "Last attested",  p.getLastAttestedAt() == null ? "—" : p.getLastAttestedAt().format(DT));
        addMeta(meta, "Attested by",    nz(p.getLastAttestedByName()));
        addMeta(meta, "Review state",   nz(p.getReviewState()));
        addMeta(meta, "Plan code",      p.getPlanCode());

        doc.add(meta);

        Paragraph stamp = new Paragraph(
            "Generated by TSS GRC Platform · " + java.time.LocalDateTime.now().format(DT),
            PdfFonts.small(9));
        stamp.setSpacingBefore(60);
        doc.add(stamp);
    }

    private static void addMeta(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, PdfFonts.small(9)));
        PdfPCell v = new PdfPCell(new Phrase(value, PdfFonts.normal(10)));
        l.setBorder(0); v.setBorder(0);
        l.setPaddingBottom(6); v.setPaddingBottom(6);
        t.addCell(l); t.addCell(v);
    }

    // ─── Sections ──────────────────────────────────────────────────────

    private void renderSections(Document doc, List<BcpSection> secs) throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Sections (ISO 22301 §8.4.2)", PdfFonts.bold(14));
        head.setSpacingAfter(12);
        doc.add(head);

        if (secs.isEmpty()) {
            doc.add(new Paragraph("(no sections)", PdfFonts.small(10)));
            return;
        }

        for (BcpSection s : secs) {
            Paragraph titleEn = new Paragraph(s.getSectionOrder() + ". " + nz(s.getSectionTitleEn()),
                PdfFonts.bold(12));
            titleEn.setSpacingBefore(10);
            titleEn.setSpacingAfter(2);
            doc.add(titleEn);

            if (s.getSectionTitleAr() != null && !s.getSectionTitleAr().isBlank()) {
                Paragraph titleAr = new Paragraph(s.getSectionTitleAr(), PdfFonts.small(10));
                titleAr.setSpacingAfter(4);
                doc.add(titleAr);
            }

            String bodyEn = nz(s.getBodyEn());
            if (!bodyEn.isBlank()) {
                Paragraph p = new Paragraph(bodyEn, PdfFonts.normal(10));
                p.setSpacingAfter(4);
                doc.add(p);
            }
            if (s.getBodyAr() != null && !s.getBodyAr().isBlank()) {
                Paragraph p = new Paragraph(s.getBodyAr(), PdfFonts.normal(10));
                p.setSpacingAfter(10);
                doc.add(p);
            }
        }
    }

    // ─── Version history ───────────────────────────────────────────────

    private void renderVersionHistory(Document doc, List<BcpVersion> vers) throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Version history", PdfFonts.bold(14));
        head.setSpacingAfter(10);
        doc.add(head);

        if (vers.isEmpty()) {
            doc.add(new Paragraph("(no approved versions yet)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        try { t.setWidths(new float[] {1, 3, 3, 7}); } catch (DocumentException ignored) {}
        headerCell(t, "Version");
        headerCell(t, "Approved at");
        headerCell(t, "By");
        headerCell(t, "Change summary");

        for (BcpVersion v : vers) {
            cell(t, "v" + v.getVersionNumber());
            cell(t, v.getApprovedAt() == null ? "—" : v.getApprovedAt().format(DT));
            cell(t, nz(v.getApprovedBy()));
            cell(t, nz(v.getChangeSummary()));
        }
        doc.add(t);
    }

    // ─── Distribution roster ───────────────────────────────────────────

    private void renderDistributionRoster(Document doc, List<BcpPlanAcknowledgement> dist) throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Distribution & acknowledgement", PdfFonts.bold(14));
        head.setSpacingAfter(10);
        doc.add(head);

        if (dist.isEmpty()) {
            doc.add(new Paragraph("(plan has not been distributed yet)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(6);
        t.setWidthPercentage(100);
        try { t.setWidths(new float[] {1, 4, 3, 2, 3, 2}); } catch (DocumentException ignored) {}
        headerCell(t, "Ver.");
        headerCell(t, "Recipient");
        headerCell(t, "Role");
        headerCell(t, "Channel");
        headerCell(t, "Distributed");
        headerCell(t, "Status");

        for (BcpPlanAcknowledgement a : dist) {
            cell(t, "v" + a.getPlanVersion());
            cell(t, nz(a.getRecipientName()) +
                (a.getRecipientEmail() == null ? "" : " <" + a.getRecipientEmail() + ">"));
            cell(t, nz(a.getRecipientRole()));
            cell(t, nz(a.getDistributionChannel()));
            cell(t, a.getDistributedAt() == null ? "—" : a.getDistributedAt().format(DT));
            cell(t, nz(a.getStatus()));
        }
        doc.add(t);
    }

    // ─── Attestation footer (last 3 attestations) ─────────────────────

    private void renderAttestationFooter(Document doc, BcpPlan p) throws DocumentException {
        List<BcpPlanReviewAttestation> rows =
            attestations.findByOrganisationIdAndPlanIdOrderByAttestedAtDesc(p.getOrganisationId(), p.getId());
        if (rows.isEmpty()) return;

        Paragraph head = new Paragraph("Recent attestations", PdfFonts.bold(12));
        head.setSpacingBefore(20);
        head.setSpacingAfter(6);
        doc.add(head);

        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            BcpPlanReviewAttestation a = rows.get(i);
            String line = "• " + a.getAttestedAt().format(DT)
                + " · " + nz(a.getAttestedByName())
                + " · " + nz(a.getAttestationOutcome())
                + (a.getNotes() == null || a.getNotes().isBlank() ? "" : " — " + a.getNotes());
            Paragraph para = new Paragraph(line, PdfFonts.small(9));
            para.setSpacingAfter(3);
            doc.add(para);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private static void headerCell(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, PdfFonts.header(10)));
        c.setBackgroundColor(new Color(0x09, 0x42, 0x42));
        c.setPaddingTop(6); c.setPaddingBottom(6);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }
    private static void cell(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, PdfFonts.normal(9)));
        c.setPaddingTop(5); c.setPaddingBottom(5);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }
    private static String nz(String s) { return s == null ? "—" : s; }

    // ─── Page event: footer with page number + plan code ─────────────

    private static final class PlanPageEvent extends PdfPageEventHelper {
        private final String label;
        PlanPageEvent(String label) { this.label = label; }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                Phrase left  = new Phrase(label, PdfFonts.small(8));
                Phrase right = new Phrase("p. " + writer.getPageNumber(), PdfFonts.small(8));
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,  left,  doc.left(),  doc.bottom() - 18, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, right, doc.right(), doc.bottom() - 18, 0);
            } catch (Exception ignored) {}
        }
    }
}
