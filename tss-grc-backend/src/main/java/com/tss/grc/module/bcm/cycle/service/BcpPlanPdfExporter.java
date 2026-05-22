package com.tss.grc.module.bcm.cycle.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfFonts;
import com.tss.grc.module.reporting.service.PdfTheme;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * BCP / DRP PDF exporter — audit Tier-2 #12.
 *
 * <p>Renders an approved/published plan as a single executive-grade PDF that
 * an auditor, regulator, or recipient can read offline. Layout:
 *
 * <ol>
 *   <li><b>Cover hero</b> — brand band, eyebrow tag, plan code + bilingual
 *       title, organisation name, Hijri + Gregorian generation stamp, and
 *       a classification banner. Followed by a KPI strip for the four
 *       continuity metrics auditors look for first: Version, RTO, RPO,
 *       MTPD. The supporting metadata (status, owner, last-attested, etc.)
 *       sits below as a borderless key/value grid.</li>
 *   <li><b>Sections</b> — one page per ISO 22301 §8.4.2 named section,
 *       with EN body followed by AR body (when present).</li>
 *   <li><b>Version history</b> — every prior approval with approver + date
 *       + change summary.</li>
 *   <li><b>Distribution roster</b> — who received which version + ack status.</li>
 *   <li><b>Recent attestations</b> — last 3 reviewer sign-offs as a tail
 *       block on the distribution page.</li>
 * </ol>
 *
 * <p>Visual identity comes from the shared {@link PdfTheme} primitives, so
 * every TSS GRC PDF (BCP, crisis, board pack, NDI report) renders with the
 * same brand palette, typography, KPI tiles, and footer style.
 *
 * <p>Bilingual via {@link PdfFonts} which loads an Arabic-capable font on
 * startup and falls back gracefully if none is available. The document
 * stays LTR throughout; Arabic blocks are rendered with the same font so
 * the right-to-left script direction comes from the font shaper, not the
 * document run direction (avoids flipping English titles + section keys).
 */
@Service
@RequiredArgsConstructor
public class BcpPlanPdfExporter {

    private static final DateTimeFormatter DT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BcpPlanRepository                       plans;
    private final BcpSectionRepository                    sections;
    private final BcpVersionRepository                    versions;
    private final BcpPlanAcknowledgementRepository        acks;
    private final BcpPlanReviewAttestationRepository      attestations;
    private final OrganisationRepository                  orgs;
    private final AuditLogService                         audit;

    @Transactional(readOnly = true)
    public byte[] export(Long planId) {
        Long org = TenantContext.getCurrentTenant();
        BcpPlan p = plans.findByIdAndOrganisationId(planId, org)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));

        List<BcpSection>          secs  = sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(org, planId);
        List<BcpVersion>          vers  = versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(org, planId);
        List<BcpPlanAcknowledgement> dist = acks.findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(org, planId);
        Organisation organisation = orgs.findById(org).orElse(null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            // Classification is RESTRICTED for plans — they expose recovery
            // playbooks. Tenant-specific override could come from a column
            // later; defaulting to RESTRICTED matches the audit threat model.
            String docLabel = (p.getPlanType() == null ? "Plan" : p.getPlanType()) + " · " + p.getPlanCode();
            writer.setPageEvent(PdfTheme.professionalFooter(docLabel, "Restricted"));
            doc.open();

            renderCover(doc, p, organisation);
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

    private void renderCover(Document doc, BcpPlan p, Organisation o) throws DocumentException {
        PdfTheme.Cover cv = new PdfTheme.Cover();
        boolean isBcp = "BCP".equalsIgnoreCase(p.getPlanType());
        cv.eyebrow    = isBcp ? "Business Continuity Plan" : "Disaster Recovery Plan";
        cv.eyebrowAr  = isBcp ? "خطة استمرارية الأعمال"     : "خطة التعافي من الكوارث";
        cv.code       = p.getPlanCode();
        cv.titleEn    = nz(p.getTitleEn());
        cv.titleAr    = p.getTitleAr();
        cv.orgNameEn  = o == null ? null : o.getNameEn();
        cv.orgNameAr  = o == null ? null : o.getNameAr();
        cv.classification = "Restricted";
        PdfTheme.coverHero(doc, cv);

        // KPI strip — the four continuity numbers auditors check first.
        // Showing them above the metadata grid means a reader scanning the
        // cover page can answer "what are the recovery targets?" in <2s.
        List<PdfTheme.Kpi> kpis = new ArrayList<>();
        kpis.add(new PdfTheme.Kpi("Version",
            "v" + (p.getCurrentVersion() == null ? 1 : p.getCurrentVersion()),
            nz(p.getStatus())));
        kpis.add(new PdfTheme.Kpi("RTO",
            p.getRtoHours()  == null ? "—" : (p.getRtoHours()  + "h"),
            "Recovery Time"));
        kpis.add(new PdfTheme.Kpi("RPO",
            p.getRpoHours()  == null ? "—" : (p.getRpoHours()  + "h"),
            "Recovery Point"));
        kpis.add(new PdfTheme.Kpi("MTPD",
            p.getMtpdHours() == null ? "—" : (p.getMtpdHours() + "h"),
            "Max Tolerable Disruption"));
        PdfTheme.kpiStrip(doc, kpis);

        // Secondary metadata as a borderless key/value grid
        PdfPTable meta = PdfTheme.metadataGrid();
        PdfTheme.addMetaCell(meta, "Plan type",     nz(p.getPlanType()));
        PdfTheme.addMetaCell(meta, "Source BIA",    p.getSourceBiaId() == null ? "—" : ("#" + p.getSourceBiaId()));
        PdfTheme.addMetaCell(meta, "Response team", nz(p.getResponseTeamRole()));
        PdfTheme.addMetaCell(meta, "Review state",  nz(p.getReviewState()));
        PdfTheme.addMetaCell(meta, "Approved by",   nz(p.getApprovedBy()));
        PdfTheme.addMetaCell(meta, "Approved at",   p.getApprovedAt()  == null ? "—" : p.getApprovedAt().format(DT));
        PdfTheme.addMetaCell(meta, "Published at",  p.getPublishedAt() == null ? "—" : p.getPublishedAt().format(DT));
        PdfTheme.addMetaCell(meta, "Next review",   p.getNextReviewDate() == null ? "—" : p.getNextReviewDate().format(DATE));
        PdfTheme.addMetaCell(meta, "Last attested", p.getLastAttestedAt() == null ? "—" : p.getLastAttestedAt().format(DT));
        PdfTheme.addMetaCell(meta, "Attested by",   nz(p.getLastAttestedByName()));
        doc.add(meta);
    }

    // ─── Sections ──────────────────────────────────────────────────────

    private void renderSections(Document doc, List<BcpSection> secs) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Plan Sections", "أقسام الخطة — ISO 22301 §8.4.2");

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
                Paragraph p = new Paragraph(bodyEn, PdfFonts.body(10));
                p.setSpacingAfter(4);
                doc.add(p);
            }
            if (s.getBodyAr() != null && !s.getBodyAr().isBlank()) {
                Paragraph p = new Paragraph(s.getBodyAr(), PdfFonts.body(10));
                p.setSpacingAfter(10);
                doc.add(p);
            }
        }
    }

    // ─── Version history ───────────────────────────────────────────────

    private void renderVersionHistory(Document doc, List<BcpVersion> vers) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Version History", "سجل الإصدارات");

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
        PdfTheme.sectionDivider(doc, "Distribution & Acknowledgement", "التوزيع والإقرار");

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
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(6); c.setPaddingBottom(6);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }
    private static void cell(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, PdfFonts.body(9)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(5); c.setPaddingBottom(5);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        t.addCell(c);
    }
    private static String nz(String s) { return s == null ? "—" : s; }
}
