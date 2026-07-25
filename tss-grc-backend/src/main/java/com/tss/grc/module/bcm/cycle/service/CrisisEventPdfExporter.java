package com.tss.grc.module.bcm.cycle.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.exception.ExportException;
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
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Crisis-event PDF exporter — audit Tier-2 #12.
 *
 * <p>Produces the after-action / incident summary PDF an auditor or
 * regulator can request after a crisis is closed. Sections:
 *
 * <ol>
 *   <li><b>Cover hero</b> — brand band, event code + bilingual title,
 *       organisation name, Hijri + Gregorian stamp, classification banner.
 *       KPI strip exposes the four numbers regulators look for first:
 *       severity, status, regulatory-exposure flags (PDPL/Cyber), and
 *       outage duration. Secondary metadata (timestamps, owner, trigger,
 *       crisis manager) sits below as a borderless key/value grid.</li>
 *   <li>Description + root cause + activated plans + affected scope</li>
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
    private final OrganisationRepository                    orgs;
    private final AuditLogService                           audit;

    @Transactional(readOnly = true)
    public byte[] export(Long eventId) {
        return export(eventId, "en");
    }

    /** @param lang pass "ar" to flip the document + tables to RTL run direction (P3).
     *  Default "en" leaves output byte-identical to the pre-lang behaviour. */
    @Transactional(readOnly = true)
    public byte[] export(Long eventId, String lang) {
        boolean ar = "ar".equalsIgnoreCase(lang);
        int rd = PdfTheme.runDir(ar);
        Long org = TenantContext.getCurrentTenant();
        CrisisEvent e = events.findByIdAndOrganisationId(eventId, org)
            .orElseThrow(() -> new EntityNotFoundException("Crisis event not found: " + eventId));

        List<CrisisSituationReport>        srs = sitreps  .findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(org, eventId);
        List<CrisisDecision>               ds  = decisions.findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(org, eventId);
        List<CrisisRegulatoryNotification> rns = regNotifs.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(org, eventId);
        Organisation organisation = orgs.findById(org).orElse(null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setRunDirection(rd);
            // Crisis after-action reports are CONFIDENTIAL — they contain
            // incident timelines and regulator interactions that aren't
            // released externally without legal review.
            writer.setPageEvent(PdfTheme.professionalFooter(
                "Crisis · " + e.getEventCode(), "Confidential"));
            doc.open();

            renderCover(doc, e, organisation, rd);
            renderNarrative(doc, e);
            renderSitReps(doc, srs, rd);
            renderDecisions(doc, ds, rd);
            renderRegulatoryNotifications(doc, rns, rd);

            doc.close();
            writer.close();
        } catch (DocumentException ex) {
            throw new ExportException("PDF", "Crisis-event PDF generation failed", ex);
        }

        audit.log("EXPORT_PDF", "CrisisEvent", eventId,
            "PDF exported for crisis event " + e.getEventCode());

        return baos.toByteArray();
    }

    // ─── Cover ────────────────────────────────────────────────────────

    private void renderCover(Document doc, CrisisEvent e, Organisation o, int rd) throws DocumentException {
        PdfTheme.Cover cv = new PdfTheme.Cover();
        cv.eyebrow    = "Crisis Event — After-Action Report";
        cv.eyebrowAr  = "حدث أزمة — تقرير ما بعد الحدث";
        cv.code       = e.getEventCode();
        cv.titleEn    = nz(e.getTitleEn());
        cv.titleAr    = e.getTitleAr();
        cv.orgNameEn  = o == null ? null : o.getNameEn();
        cv.orgNameAr  = o == null ? null : o.getNameAr();
        cv.classification = "Confidential";
        PdfTheme.coverHero(doc, cv);

        // KPI strip — the four headline numbers a regulator scans first.
        // Severity drives the chip colour we apply below the strip; the
        // boolean flags collapse into a single "Regulatory" indicator so
        // the row stays at exactly four tiles.
        long durationH = durationHours(e.getStartTime(), e.getClosureTime());
        String regFlags = (Boolean.TRUE.equals(e.getPersonalDataAffected()) ? "PDPL " : "")
                        + (Boolean.TRUE.equals(e.getCyberRelated())         ? "NCA "  : "")
                        + (Boolean.TRUE.equals(e.getSupplierInvolved())     ? "TPRM"  : "");
        if (regFlags.isBlank()) regFlags = "None";

        List<PdfTheme.Kpi> kpis = new ArrayList<>();
        kpis.add(new PdfTheme.Kpi("Severity",
            severityLabel(e.getSeverity()),
            "Crisis tier"));
        kpis.add(new PdfTheme.Kpi("Status",
            nz(e.getStatus()),
            nz(e.getTriggerSource())));
        kpis.add(new PdfTheme.Kpi("Duration",
            durationH < 0 ? "—" : (durationH + "h"),
            "Start → Closure"));
        kpis.add(new PdfTheme.Kpi("Regulatory",
            regFlags.trim(),
            "Notification triggers"));
        PdfTheme.kpiStrip(doc, kpis);

        // Severity chip — small coloured pill keyed to the numeric tier
        if (e.getSeverity() != null) {
            PdfPTable chipWrap = new PdfPTable(2);
            chipWrap.setRunDirection(rd);
            chipWrap.setWidthPercentage(100);
            try { chipWrap.setWidths(new float[] {1, 9}); } catch (DocumentException ignored) {}
            PdfPCell label = new PdfPCell(new Phrase("SEVERITY", PdfFonts.kpiLabel(8)));
            label.setBorder(0); label.setPaddingTop(4); label.setPaddingBottom(8);
            chipWrap.addCell(label);
            PdfPCell chipCell = new PdfPCell();
            chipCell.setBorder(0);
            chipCell.addElement(PdfTheme.statusChip(severityLabel(e.getSeverity()),
                severityChipKind(e.getSeverity())));
            chipWrap.addCell(chipCell);
            doc.add(chipWrap);
        }

        // Secondary metadata — timestamps, owner, related entities
        PdfPTable meta = PdfTheme.metadataGrid();
        PdfTheme.addMetaCell(meta, "Detected at",       fmt(e.getDetectionTime()));
        PdfTheme.addMetaCell(meta, "Activated at",      fmt(e.getActivationTime()));
        PdfTheme.addMetaCell(meta, "Started at",        fmt(e.getStartTime()));
        PdfTheme.addMetaCell(meta, "Closed at",         fmt(e.getClosureTime()));
        PdfTheme.addMetaCell(meta, "Crisis manager",    e.getCrisisManagerId() == null ? "—" : ("#" + e.getCrisisManagerId()));
        PdfTheme.addMetaCell(meta, "Trigger",           nz(e.getTriggerSource()));
        PdfTheme.addMetaCell(meta, "PDPL breach",       Boolean.TRUE.equals(e.getPersonalDataAffected()) ? "Yes" : "No");
        PdfTheme.addMetaCell(meta, "Cyber-related",     Boolean.TRUE.equals(e.getCyberRelated())          ? "Yes" : "No");
        PdfTheme.addMetaCell(meta, "Supplier-related",  Boolean.TRUE.equals(e.getSupplierInvolved())      ? "Yes" : "No");
        PdfTheme.addMetaCell(meta, "Lessons row",       e.getLessonsLearnedId() == null ? "—" : ("#" + e.getLessonsLearnedId()));
        doc.add(meta);
    }

    // ─── Narrative ────────────────────────────────────────────────────

    private void renderNarrative(Document doc, CrisisEvent e) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Description & Root Cause", "الوصف والسبب الجذري");

        // User-entered narrative is frequently Arabic — route through the
        // shaping-aware line() (Arabic-leading text right-aligns + shapes;
        // Latin text keeps its left start).
        if (e.getDescription() != null && !e.getDescription().isBlank()) {
            PdfTheme.line(doc, e.getDescription(), PdfFonts.body(10),
                PdfTheme.firstStrongIsArabic(e.getDescription()), 0, 10);
        } else {
            doc.add(new Paragraph("(no description recorded)", PdfFonts.small(10)));
        }

        if (e.getRootCause() != null && !e.getRootCause().isBlank()) {
            Paragraph rcLabel = new Paragraph("Root cause", PdfFonts.bold(11));
            rcLabel.setSpacingBefore(10);
            rcLabel.setSpacingAfter(4);
            doc.add(rcLabel);

            PdfTheme.line(doc, e.getRootCause(), PdfFonts.body(10),
                PdfTheme.firstStrongIsArabic(e.getRootCause()), 0, 10);
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
        if (PdfTheme.hasArabic(json)) {
            // Arabic service/site names inside the JSON — shape the whole line.
            PdfTheme.line(doc, label + ": " + json, PdfFonts.small(9), false, 0, 3);
            return;
        }
        Paragraph p = new Paragraph();
        p.add(new Phrase(label + ": ", PdfFonts.bold(10)));
        p.add(new Phrase(json, PdfFonts.small(9)));
        p.setSpacingAfter(3);
        doc.add(p);
    }

    // ─── SitReps ──────────────────────────────────────────────────────

    private void renderSitReps(Document doc, List<CrisisSituationReport> srs, int rd) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Situation Reports", "تقارير الموقف");

        if (srs.isEmpty()) {
            doc.add(new Paragraph("(no SitReps recorded)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(3);
        t.setRunDirection(rd);
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

    private void renderDecisions(Document doc, List<CrisisDecision> ds, int rd) throws DocumentException {
        PdfTheme.sectionDivider(doc, "Decisions Log", "سجل القرارات");

        if (ds.isEmpty()) {
            doc.add(new Paragraph("(no decisions recorded)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(4);
        t.setRunDirection(rd);
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

    private void renderRegulatoryNotifications(Document doc, List<CrisisRegulatoryNotification> rns, int rd)
            throws DocumentException {
        PdfTheme.sectionDivider(doc, "Regulatory Notifications", "إخطارات الجهات التنظيمية");

        if (rns.isEmpty()) {
            doc.add(new Paragraph("(no regulator notifications recorded)", PdfFonts.small(10)));
            return;
        }

        PdfPTable t = new PdfPTable(5);
        t.setRunDirection(rd);
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

    /** Maps severity tier → chip colour. 4/5 are red, 3 amber, 1/2 blue. */
    private static PdfTheme.ChipKind severityChipKind(Integer sev) {
        if (sev == null) return PdfTheme.ChipKind.NEUTRAL;
        return switch (sev) {
            case 5, 4 -> PdfTheme.ChipKind.DANGER;
            case 3    -> PdfTheme.ChipKind.WARNING;
            case 2    -> PdfTheme.ChipKind.INFO;
            case 1    -> PdfTheme.ChipKind.SUCCESS;
            default   -> PdfTheme.ChipKind.NEUTRAL;
        };
    }

    /** Returns hours between two timestamps, or -1 if either is null. */
    private static long durationHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return -1;
        return java.time.Duration.between(start, end).toHours();
    }

    private static String fmt(LocalDateTime t) { return t == null ? "—" : t.format(DT); }
    private static String nz(String s) { return s == null ? "—" : s; }

    private static void headerCell(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, PdfFonts.header(10)));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(6); c.setPaddingBottom(6);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        PdfTheme.shapeCellIfArabic(t, c, txt);
        t.addCell(c);
    }
    private static void cell(PdfPTable t, String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt, PdfFonts.body(9)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(5); c.setPaddingBottom(5);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        PdfTheme.shapeCellIfArabic(t, c, txt);
        t.addCell(c);
    }
}
