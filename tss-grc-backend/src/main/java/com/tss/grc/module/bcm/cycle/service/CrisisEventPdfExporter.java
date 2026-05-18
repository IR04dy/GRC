package com.tss.grc.module.bcm.cycle.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisDecision;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.entity.CrisisRegulatoryNotification;
import com.tss.grc.module.bcm.cycle.entity.CrisisSituationReport;
import com.tss.grc.module.bcm.cycle.repository.CrisisDecisionRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisRegulatoryNotificationRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisSituationReportRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.reporting.service.PdfFonts;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Crisis-event PDF exporter — audit Tier-2 #12.
 *
 * <p>Produces the after-action / incident summary PDF an auditor or
 * regulator can request after a crisis is closed. Sections:
 *
 * <ol>
 *   <li>Cover — event code, title, severity, status, scope, timing</li>
 *   <li>Description + root cause + activated plans</li>
 *   <li>SitReps timeline (oldest first)</li>
 *   <li>Decisions log (oldest first)</li>
 *   <li>Regulatory notifications log with reference numbers + ack status</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CrisisEventPdfExporter {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CrisisEventRepository                     events;
    private final CrisisSituationReportRepository           sitreps;
    private final CrisisDecisionRepository                  decisions;
    private final CrisisRegulatoryNotificationRepository    regNotifs;
    private final AuditLogService                           audit;

    @Transactional(readOnly = true)
    public byte[] export(Long eventId) {
        Long org = TenantContext.getCurrentTenant();
        CrisisEvent e = events.findByIdAndOrganisationId(eventId, org)
            .orElseThrow(() -> new EntityNotFoundException("Crisis event not found: " + eventId));

        List<CrisisSituationReport>        srs   = sitreps   .findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(org, eventId);
        List<CrisisDecision>               ds    = decisions .findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(org, eventId);
        List<CrisisRegulatoryNotification> rns   = regNotifs .findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(org, eventId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new EventPageEvent("Crisis · " + e.getEventCode()));
            doc.open();

            renderCover(doc, e);
            renderNarrative(doc, e);
            renderSitReps(doc, srs);
            renderDecisions(doc, ds);
            renderRegulatoryNotifications(doc, rns);

            doc.close();
            writer.close();
        } catch (DocumentException ex) {
            throw new RuntimeException("Crisis-event PDF generation failed", ex);
        }

        audit.log("EXPORT_PDF", "CrisisEvent", eventId,
            "PDF exported for crisis event " + e.getEventCode());

        return baos.toByteArray();
    }

    // ─── Cover ────────────────────────────────────────────────────────

    private void renderCover(Document doc, CrisisEvent e) throws DocumentException {
        Paragraph eyebrow = new Paragraph("Crisis Event — After-Action Report", PdfFonts.small(10));
        eyebrow.setSpacingBefore(80);
        doc.add(eyebrow);

        Paragraph code = new Paragraph(e.getEventCode(), PdfFonts.bold(20));
        code.setSpacingAfter(4);
        doc.add(code);

        Paragraph title = new Paragraph(nz(e.getTitleEn()), PdfFonts.bold(16));
        title.setSpacingAfter(2);
        doc.add(title);

        if (e.getTitleAr() != null && !e.getTitleAr().isBlank()) {
            Paragraph titleAr = new Paragraph(e.getTitleAr(), PdfFonts.normal(14));
            titleAr.setSpacingAfter(20);
            doc.add(titleAr);
        }

        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(20);
        try { meta.setWidths(new float[] {1, 2, 1, 2}); } catch (DocumentException ignored) {}

        addMeta(meta, "Severity",       severityLabel(e.getSeverity()));
        addMeta(meta, "Status",         nz(e.getStatus()));
        addMeta(meta, "Trigger",        nz(e.getTriggerSource()));
        addMeta(meta, "Crisis manager", e.getCrisisManagerId() == null ? "—" : ("#" + e.getCrisisManagerId()));

        addMeta(meta, "Detected at",    fmt(e.getDetectionTime()));
        addMeta(meta, "Activated at",   fmt(e.getActivationTime()));
        addMeta(meta, "Started at",     fmt(e.getStartTime()));
        addMeta(meta, "Closed at",      fmt(e.getClosureTime()));

        addMeta(meta, "PDPL breach",   Boolean.TRUE.equals(e.getPersonalDataAffected()) ? "Yes" : "No");
        addMeta(meta, "Cyber-related", Boolean.TRUE.equals(e.getCyberRelated())          ? "Yes" : "No");
        addMeta(meta, "Supplier-related", Boolean.TRUE.equals(e.getSupplierInvolved())   ? "Yes" : "No");
        addMeta(meta, "Lessons row",   e.getLessonsLearnedId() == null ? "—" : ("#" + e.getLessonsLearnedId()));

        doc.add(meta);

        Paragraph stamp = new Paragraph(
            "Generated by TSS GRC Platform · " + LocalDateTime.now().format(DT),
            PdfFonts.small(9));
        stamp.setSpacingBefore(40);
        doc.add(stamp);
    }

    // ─── Narrative ────────────────────────────────────────────────────

    private void renderNarrative(Document doc, CrisisEvent e) throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Description & root cause", PdfFonts.bold(14));
        head.setSpacingAfter(10);
        doc.add(head);

        if (e.getDescription() != null && !e.getDescription().isBlank()) {
            Paragraph desc = new Paragraph(e.getDescription(), PdfFonts.normal(10));
            desc.setSpacingAfter(10);
            doc.add(desc);
        } else {
            doc.add(new Paragraph("(no description recorded)", PdfFonts.small(10)));
        }

        if (e.getRootCause() != null && !e.getRootCause().isBlank()) {
            Paragraph rcLabel = new Paragraph("Root cause", PdfFonts.bold(11));
            rcLabel.setSpacingBefore(10);
            rcLabel.setSpacingAfter(4);
            doc.add(rcLabel);

            Paragraph rc = new Paragraph(e.getRootCause(), PdfFonts.normal(10));
            rc.setSpacingAfter(10);
            doc.add(rc);
        }

        // Affected-scope JSON arrays as labelled lists
        affectedLine(doc, "Affected services",   e.getAffectedServicesJson());
        affectedLine(doc, "Affected processes",  e.getAffectedProcessesJson());
        affectedLine(doc, "Affected sites",      e.getAffectedSitesJson());
        affectedLine(doc, "Affected suppliers",  e.getAffectedSuppliersJson());
        affectedLine(doc, "Affected systems",    e.getAffectedSystemsJson());
        affectedLine(doc, "Activated BCPs",      e.getRelatedBcpIdsJson());
        affectedLine(doc, "Activated DRPs",      e.getRelatedDrpIdsJson());
    }

    private void affectedLine(Document doc, String label, String json) throws DocumentException {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return;
        Paragraph p = new Paragraph();
        p.add(new Phrase(label + ": ", PdfFonts.bold(10)));
        p.add(new Phrase(json, PdfFonts.small(9)));
        p.setSpacingAfter(3);
        doc.add(p);
    }

    // ─── SitReps ──────────────────────────────────────────────────────

    private void renderSitReps(Document doc, List<CrisisSituationReport> srs) throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Situation reports", PdfFonts.bold(14));
        head.setSpacingAfter(10);
        doc.add(head);

        if (srs.isEmpty()) {
            doc.add(new Paragraph("(no SitReps recorded)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        try { t.setWidths(new float[] {3, 2, 9}); } catch (DocumentException ignored) {}
        headerCell(t, "Reported at");
        headerCell(t, "Status update");
        headerCell(t, "Summary");

        for (CrisisSituationReport s : srs) {
            cell(t, fmt(s.getReportedAt()));
            cell(t, nz(s.getStatusUpdate()));
            cell(t, nz(s.getSummary()));
        }
        doc.add(t);
    }

    // ─── Decisions ────────────────────────────────────────────────────

    private void renderDecisions(Document doc, List<CrisisDecision> ds) throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Decisions log", PdfFonts.bold(14));
        head.setSpacingAfter(10);
        doc.add(head);

        if (ds.isEmpty()) {
            doc.add(new Paragraph("(no decisions recorded)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        try { t.setWidths(new float[] {3, 3, 5, 5}); } catch (DocumentException ignored) {}
        headerCell(t, "Decided at");
        headerCell(t, "By");
        headerCell(t, "Decision");
        headerCell(t, "Rationale / action");

        for (CrisisDecision d : ds) {
            cell(t, fmt(d.getDecidedAt()));
            cell(t, nz(d.getDecidedByName()));
            cell(t, nz(d.getDecisionText()));
            String rhs = nz(d.getRationale());
            if (d.getActionRequired() != null && !d.getActionRequired().isBlank()) {
                rhs = rhs + "  ▸ " + d.getActionRequired();
            }
            cell(t, rhs);
        }
        doc.add(t);
    }

    // ─── Regulatory notifications ─────────────────────────────────────

    private void renderRegulatoryNotifications(Document doc, List<CrisisRegulatoryNotification> rns)
            throws DocumentException {
        doc.newPage();
        Paragraph head = new Paragraph("Regulatory notifications", PdfFonts.bold(14));
        head.setSpacingAfter(10);
        doc.add(head);

        if (rns.isEmpty()) {
            doc.add(new Paragraph("(no regulator notifications recorded)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        try { t.setWidths(new float[] {2, 3, 3, 4, 4}); } catch (DocumentException ignored) {}
        headerCell(t, "Regulator");
        headerCell(t, "Notified at");
        headerCell(t, "Method");
        headerCell(t, "Reference / Ack");
        headerCell(t, "Body summary");

        for (CrisisRegulatoryNotification r : rns) {
            cell(t, nz(r.getRegulator()));
            cell(t, fmt(r.getNotifiedAt()));
            cell(t, nz(r.getNotificationMethod()));
            String refAck = nz(r.getReferenceNumber())
                + (Boolean.TRUE.equals(r.getAcknowledgementReceived())
                    ? "\nack: " + nz(r.getAcknowledgementReference())
                    : "\n(no ack yet)");
            cell(t, refAck);
            cell(t, nz(r.getBodySummary()));
        }
        doc.add(t);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private static String severityLabel(Integer sev) {
        if (sev == null) return "—";
        return switch (sev) {
            case 1 -> "1 — Operational Event";
            case 2 -> "2 — Significant Incident";
            case 3 -> "3 — Major Disruption";
            case 4 -> "4 — Crisis";
            case 5 -> "5 — National Emergency";
            default -> String.valueOf(sev);
        };
    }

    private static String fmt(LocalDateTime t) { return t == null ? "—" : t.format(DT); }
    private static String nz(String s) { return s == null ? "—" : s; }

    private static void addMeta(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, PdfFonts.small(9)));
        PdfPCell v = new PdfPCell(new Phrase(value, PdfFonts.normal(10)));
        l.setBorder(0); v.setBorder(0);
        l.setPaddingBottom(6); v.setPaddingBottom(6);
        t.addCell(l); t.addCell(v);
    }
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

    private static final class EventPageEvent extends PdfPageEventHelper {
        private final String label;
        EventPageEvent(String label) { this.label = label; }
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
