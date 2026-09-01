package com.tss.grc.module.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.service.CommitteeAgendaJson;
import com.tss.grc.module.platform.export.PdfFonts;
import com.tss.grc.module.platform.export.PdfTheme;
import com.tss.grc.module.platform.export.XlsxStyler;

// Explicit imports — wildcard com.lowagie.text.* collides with
// org.apache.poi.ss.usermodel.* on Cell and Row.
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.*;
import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeLink;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.platform.export.ExportAuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bilingual operational-report export service (Q1) using OpenPDF.
 *
 * <p>Produces the three core register exports the platform offers as
 * download buttons across the UI: Risk Register, Findings Aging, and
 * KRI Dashboard. Each method emits both a leadership-grade PDF (cover
 * hero + KPI strip + branded table + classification footer) and a
 * tabular XLSX with live formulas for hands-on analysis.
 *
 * <p>The PDF side uses the shared {@link PdfTheme} primitives so every
 * report looks identical to the BCP/Crisis/NDI exporters and the board
 * pack — one brand identity across the whole platform's printed output.
 *
 * <p>Locale handling: pass {@code lang = "ar"} for a fully RTL document —
 * cover, KPI strip, metadata grid, and tables all lay out right-to-left
 * with OpenPDF's bidi shaper joining the Arabic letters (every element
 * carries an explicit run direction; unshaped/LTR Arabic shipped once).
 * Latin fragments (R-00012 codes) coexist as LTR runs within RTL lines.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriMeasurementRepository measRepo;
    private final BrandThemeService brandThemeService;
    private final ChangeRequestRepository changeRepo;   // CH-4: change register export
    private final ChangeApprovalRepository changeApprovalRepo; // CH-11: CAB pack
    private final ChangeTaskRepository changeTaskRepo;         // CH-11: CAB pack
    private final ChangeLinkRepository changeLinkRepo;         // CH-11: CAB pack
    private final PolicyDocumentRepository policyDocRepo;      // CH-10: controlled policy PDF
    private final CommitteeMeetingRepository meetingRepo;      // S7f/CE-14a: agenda + minutes
    private final CommitteeResolutionRepository resolutionRepo; // S7f/CE-14a: minutes
    private final ChangeDecisionRegisterService decisionRegisterService; // S8d/CE-14b: decision register

    /** S7f: the meetings' `*_json` snapshot columns. One mapper, not one per call. */
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Risk Register ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] riskRegisterPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        ExportAuditContext.record(risks.size(), 1, "ExportService.riskRegister");   // F2-41

        // KPI counts — surfaced on the cover so a reader sees the headline
        // numbers before paging into the row dump.
        long total    = risks.size();
        long critical = risks.stream().filter(r -> "CRITICAL".equalsIgnoreCase(r.getRiskLevel())).count();
        long high     = risks.stream().filter(r -> "HIGH".equalsIgnoreCase(r.getRiskLevel())).count();
        long open     = risks.stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus())).count();

        return buildReportPdf(o, ar,
            "Risk Register",      "سجل المخاطر",
            "Enterprise Risk Register", "سجل المخاطر المؤسسية",
            List.of(
                new PdfTheme.Kpi("Total risks", String.valueOf(total), "All states"),
                new PdfTheme.Kpi(ar ? "حرجة" : "Critical", String.valueOf(critical), "Need attention"),
                new PdfTheme.Kpi(ar ? "عالية" : "High",   String.valueOf(high), ""),
                new PdfTheme.Kpi(ar ? "مفتوحة" : "Open",  String.valueOf(open), "")
            ),
            new ReportMetadata("ERM", ar ? "سجل المخاطر" : "Risk Register",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(java.time.LocalDate.now()),
                ar ? "جميع المخاطر (بحد أقصى 1000)" : "All risks (max 1000)", lang, "Internal", risks.size()),
            (doc) -> {
                float[] widths = new float[]{ 1.2f, 4f, 1.6f, 1f, 1f, 1f, 1.5f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                t.setRunDirection(PdfTheme.runDir(ar));
                String[] hdr = ar
                    ? new String[]{ "الرقم", "المخاطرة", "الفئة", "الاحتمال", "الأثر", "النتيجة", "الحالة" }
                    : new String[]{ "Code",  "Risk",     "Category", "L",       "I",       "Score",   "Status" };
                for (String h : hdr) headerCell(t, h);
                for (Risk r : risks) {
                    String name = ar && r.getTitleAr() != null ? r.getTitleAr() : r.getTitleEn();
                    cell(t, "R-" + String.format(Locale.ROOT, "%05d", r.getId()));
                    cell(t, safe(name));
                    cell(t, "");
                    cell(t, String.valueOf(r.getInherentLikelihood()));
                    cell(t, String.valueOf(r.getInherentImpact()));
                    cell(t, String.valueOf(r.getInherentScore()));
                    cell(t, safe(r.getStatus()));
                }
                doc.add(t);
            });
    }

    // ── Change register (CH-4) ─────────────────────────────────────────────
    // Fixes the review defect where the Changes "Export" button downloaded the
    // RISK register. This is a real change-register export (PDF + XLSX; DOCX in
    // DocxExportService), org-scoped, RTL-aware, mirroring the risk exporters.

    @Transactional(readOnly = true)
    public byte[] changeRegisterPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<ChangeRequest> changes = changeRepo
            .findByOrganisationIdOrderByCreatedAtDesc(org, PageRequest.of(0, 1000)).getContent();
        ExportAuditContext.record(changes.size(), 1, "ExportService.changeRegister");   // F2-41
        long total     = changes.size();
        long emergency = changes.stream().filter(c -> "EMERGENCY".equalsIgnoreCase(c.getChangeType())).count();
        long open      = changes.stream().filter(c ->
            !"CLOSED".equalsIgnoreCase(c.getStatus()) && !"CANCELLED".equalsIgnoreCase(c.getStatus())).count();
        long closed    = changes.stream().filter(c -> "CLOSED".equalsIgnoreCase(c.getStatus())).count();

        return buildReportPdf(o, ar,
            "Change Management", "إدارة التغيير",
            "Change Register",   "سجل التغييرات",
            List.of(
                new PdfTheme.Kpi(ar ? "الإجمالي" : "Total",    String.valueOf(total),     "All states"),
                new PdfTheme.Kpi(ar ? "طارئة"   : "Emergency", String.valueOf(emergency), ar ? "مسار طارئ" : "Bypass path"),
                new PdfTheme.Kpi(ar ? "مفتوحة"  : "Open",      String.valueOf(open),      ""),
                new PdfTheme.Kpi(ar ? "مغلقة"   : "Closed",    String.valueOf(closed),    "")
            ),
            new ReportMetadata("Change", ar ? "سجل التغييرات" : "Change Register",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(java.time.LocalDate.now()),
                ar ? "جميع التغييرات (بحد أقصى 1000)" : "All changes (max 1000)", lang, "Internal", changes.size()),
            (doc) -> {
                float[] widths = new float[]{ 1.4f, 4f, 1.2f, 1f, 1.6f, 1.3f, 1.6f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                t.setRunDirection(PdfTheme.runDir(ar));
                String[] hdr = ar
                    ? new String[]{ "الرمز", "التغيير", "النوع", "الخطورة", "المالك", "تاريخ الطلب", "الحالة" }
                    : new String[]{ "Code",  "Change",  "Type",  "Risk",    "Owner",  "Requested",   "Status" };
                for (String h : hdr) headerCell(t, h);
                for (ChangeRequest c : changes) {
                    String name = ar && c.getTitleAr() != null ? c.getTitleAr() : c.getTitleEn();
                    cell(t, safe(c.getChangeCode()));
                    cell(t, safe(name));
                    cell(t, safe(c.getChangeType()));
                    cell(t, safe(c.getRiskLevel()));
                    cell(t, safe(c.getChangeOwnerName()));
                    cell(t, c.getRequestedDate() != null ? c.getRequestedDate().toString() : "");
                    cell(t, safe(c.getStatus()));
                }
                doc.add(t);
            });
    }

    @Transactional(readOnly = true)
    public byte[] changeRegisterXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<ChangeRequest> changes = changeRepo
            .findByOrganisationIdOrderByCreatedAtDesc(org, PageRequest.of(0, 1000)).getContent();
        ExportAuditContext.record(changes.size(), 1, "ExportService.changeRegister");   // F2-41

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Change Register");
            CellStyle hdrStyle = headerStyle(wb);
            CellStyle wrapStyle = wrapStyle(wb);

            String[] headers = { "Code", "Title (EN)", "Title (AR)", "Type", "Risk", "Owner",
                "Requested By", "Requested Date", "Scheduled Start", "Status", "Outcome" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }

            int rowIdx = 1;
            for (ChangeRequest c : changes) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(c.getChangeCode()));
                Cell t1 = row.createCell(1); t1.setCellValue(safe(c.getTitleEn())); t1.setCellStyle(wrapStyle);
                Cell t2 = row.createCell(2); t2.setCellValue(safe(c.getTitleAr())); t2.setCellStyle(wrapStyle);
                row.createCell(3).setCellValue(safe(c.getChangeType()));
                row.createCell(4).setCellValue(safe(c.getRiskLevel()));
                row.createCell(5).setCellValue(safe(c.getChangeOwnerName()));
                row.createCell(6).setCellValue(safe(c.getRequestedByName()));
                row.createCell(7).setCellValue(c.getRequestedDate() != null ? c.getRequestedDate().toString() : "");
                row.createCell(8).setCellValue(c.getScheduledStart() != null ? c.getScheduledStart().toString() : "");
                row.createCell(9).setCellValue(safe(c.getStatus()));
                row.createCell(10).setCellValue(safe(c.getOutcome()));
            }

            XlsxStyler.freezeAndFilter(sheet, headers.length, rowIdx - 1);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(sheet, i, 1, rowIdx - 1);
            }
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    // ── CH-11: CAB pack (single change, portrait, RTL) ─────────────────────
    //
    // A consolidated Change Advisory Board pack for ONE change: the record header
    // + KPIs, the plans/rationale, the CAB decision, the recorded votes, the
    // implementation tasks, cross-module links, and the post-implementation
    // review. This is the artifact a CAB circulates/minutes against — distinct
    // from the register export (CH-4), which lists all changes.

    @Transactional(readOnly = true)
    public byte[] cabPackPdf(Long changeId, String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        ChangeRequest c = changeRepo.findById(changeId)
            .filter(x -> x.getOrganisationId().equals(org) && !Boolean.TRUE.equals(x.getIsDeleted()))
            .orElseThrow(() -> new EntityNotFoundException("Change request not found: " + changeId));
        List<ChangeApproval> approvals = changeApprovalRepo.findByChangeRequestIdOrderByDecidedAtAsc(changeId);
        List<ChangeTask> tasks = changeTaskRepo.findByChangeRequestIdOrderBySortOrderAscIdAsc(changeId);
        List<ChangeLink> links = changeLinkRepo.findByChangeRequestId(changeId);

        long approve = approvals.stream().filter(a -> "APPROVE".equalsIgnoreCase(a.getDecision())).count();
        long reject  = approvals.stream().filter(a -> "REJECT".equalsIgnoreCase(a.getDecision())).count();
        String title = ar && c.getTitleAr() != null ? c.getTitleAr() : c.getTitleEn();

        return buildReportPdf(o, ar,
            "Change Advisory Board", "مجلس استشارة التغيير",
            (ar ? "حزمة مجلس التغيير — " : "CAB Pack — ") + safe(c.getChangeCode()),
            (ar ? "حزمة مجلس التغيير — " : "CAB Pack — ") + safe(c.getChangeCode()),
            List.of(
                new PdfTheme.Kpi(ar ? "النوع"    : "Type",   safe(c.getChangeType()), ""),
                new PdfTheme.Kpi(ar ? "الخطورة"  : "Risk",   safe(c.getRiskLevel()),  ""),
                new PdfTheme.Kpi(ar ? "الحالة"   : "Status", safe(c.getStatus()),     ""),
                new PdfTheme.Kpi(ar ? "الأصوات"  : "Votes",  approve + "/" + approvals.size(),
                    reject > 0 ? (ar ? "يوجد رفض" : "has REJECT") : "")
            ),
            new ReportMetadata("Change", ar ? "حزمة مجلس التغيير" : "CAB Pack",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(LocalDate.now()),
                safe(c.getChangeCode()), lang, "Internal", 1),
            (doc) -> {
                // Change title bar
                PdfPTable bar = fullTable(ar);
                headerCell(bar, safe(title));
                doc.add(bar);

                // Details (2-col label/value form)
                cabSection(doc, ar, "Change Details", "تفاصيل التغيير");
                PdfPTable d = kvTable(ar);
                kvRow(d, ar, "Requested by", "مقدّم الطلب", safe(c.getRequestedByName()));
                kvRow(d, ar, "Change owner", "مالك التغيير", safe(c.getChangeOwnerName()));
                kvRow(d, ar, "Implementer",  "المنفّذ",      safe(c.getImplementerName()));
                kvRow(d, ar, "Category",     "الفئة",        safe(c.getCategory()));
                kvRow(d, ar, "Requested date", "تاريخ الطلب",
                    c.getRequestedDate() != null ? c.getRequestedDate().toString() : "—");
                kvRow(d, ar, "Scheduled start", "بداية الجدولة",
                    c.getScheduledStart() != null ? c.getScheduledStart().toString() : "—");
                kvRow(d, ar, "Scheduled end", "نهاية الجدولة",
                    c.getScheduledEnd() != null ? c.getScheduledEnd().toString() : "—");
                kvRow(d, ar, "Outcome", "النتيجة", safe(c.getOutcome()));
                doc.add(d);

                // Plans & rationale (only non-blank blocks)
                cabSection(doc, ar, "Plans & Rationale", "الخطط والمبرر");
                PdfPTable p = kvTable(ar);
                kvRowIf(p, ar, "Business justification", "المبرر التجاري", c.getBusinessJustification());
                kvRowIf(p, ar, "Impact",              "الأثر",       c.getImpactSummary());
                kvRowIf(p, ar, "Implementation plan", "خطة التنفيذ", c.getImplementationPlan());
                kvRowIf(p, ar, "Test plan",           "خطة الاختبار", c.getTestPlan());
                kvRowIf(p, ar, "Rollback plan",       "خطة التراجع", c.getRollbackPlan());
                if (isBlank(c.getBusinessJustification()) && isBlank(c.getImpactSummary())
                        && isBlank(c.getImplementationPlan()) && isBlank(c.getTestPlan())
                        && isBlank(c.getRollbackPlan()))
                    kvRow(p, ar, "—", "—", ar ? "لم تُسجّل خطط" : "No plans recorded");
                doc.add(p);

                // CAB decision
                if (c.getCabDecisionAt() != null || !isBlank(c.getCabDecisionNotes())) {
                    cabSection(doc, ar, "CAB Decision", "قرار المجلس");
                    PdfPTable cd = kvTable(ar);
                    kvRow(cd, ar, "Decided at", "تاريخ القرار",
                        c.getCabDecisionAt() != null ? c.getCabDecisionAt().toString() : "—");
                    kvRowIf(cd, ar, "Notes", "ملاحظات", c.getCabDecisionNotes());
                    doc.add(cd);
                }

                // Approval votes
                cabSection(doc, ar, "CAB Votes", "أصوات المجلس");
                if (approvals.isEmpty()) {
                    PdfPTable e = fullTable(ar); cell(e, ar ? "لا توجد أصوات" : "No votes recorded"); doc.add(e);
                } else {
                    PdfPTable vt = new PdfPTable(new float[]{ 1.2f, 2f, 1.5f, 2f, 3f });
                    vt.setWidthPercentage(100); vt.setRunDirection(PdfTheme.runDir(ar));
                    for (String h : (ar ? new String[]{ "القرار", "المُصوّت", "الدور", "التاريخ", "ملاحظات" }
                                        : new String[]{ "Decision", "Approver", "Role", "Decided", "Comments" }))
                        headerCell(vt, h);
                    for (ChangeApproval a : approvals) {
                        cell(vt, safe(a.getDecision()));
                        cell(vt, safe(a.getApproverName()));
                        cell(vt, safe(a.getApproverRole()));
                        cell(vt, a.getDecidedAt() != null ? a.getDecidedAt().toString() : "—");
                        cell(vt, safe(a.getComments()));
                    }
                    doc.add(vt);
                }

                // Implementation tasks
                cabSection(doc, ar, "Implementation Tasks", "مهام التنفيذ");
                if (tasks.isEmpty()) {
                    PdfPTable e = fullTable(ar); cell(e, ar ? "لا توجد مهام" : "No tasks"); doc.add(e);
                } else {
                    PdfPTable tt = new PdfPTable(new float[]{ 4f, 2f, 1.5f, 2f });
                    tt.setWidthPercentage(100); tt.setRunDirection(PdfTheme.runDir(ar));
                    for (String h : (ar ? new String[]{ "المهمة", "المالك", "الحالة", "الاستحقاق" }
                                        : new String[]{ "Task", "Owner", "Status", "Due" }))
                        headerCell(tt, h);
                    for (ChangeTask k : tasks) {
                        String kn = ar && k.getTitleAr() != null ? k.getTitleAr() : k.getTitleEn();
                        cell(tt, safe(kn));
                        cell(tt, safe(k.getOwnerName()));
                        cell(tt, safe(k.getStatus()));
                        cell(tt, k.getDueAt() != null ? k.getDueAt().toString() : "—");
                    }
                    doc.add(tt);
                }

                // Cross-module links
                if (!links.isEmpty()) {
                    cabSection(doc, ar, "Linked Items", "العناصر المرتبطة");
                    PdfPTable lt = new PdfPTable(new float[]{ 2.5f, 1.5f, 2.5f });
                    lt.setWidthPercentage(100); lt.setRunDirection(PdfTheme.runDir(ar));
                    for (String h : (ar ? new String[]{ "النوع", "المعرّف", "نوع الربط" }
                                        : new String[]{ "Entity", "ID", "Link" }))
                        headerCell(lt, h);
                    for (ChangeLink l : links) {
                        cell(lt, safe(l.getEntityType()));
                        cell(lt, l.getEntityId() != null ? l.getEntityId().toString() : "—");
                        cell(lt, safe(l.getLinkType()));
                    }
                    doc.add(lt);
                }

                // Post-implementation review
                if (!isBlank(c.getPirSummary()) || c.getPirObjectivesMet() != null
                        || !isBlank(c.getPirLessons()) || !isBlank(c.getPirFollowUp())
                        || !isBlank(c.getPirReviewedBy())) {
                    cabSection(doc, ar, "Post-Implementation Review", "مراجعة ما بعد التنفيذ");
                    PdfPTable rt = kvTable(ar);
                    kvRowIf(rt, ar, "Summary", "الملخص", c.getPirSummary());
                    if (c.getPirObjectivesMet() != null)
                        kvRow(rt, ar, "Objectives met", "تحقّق الأهداف",
                            c.getPirObjectivesMet() ? (ar ? "نعم" : "Yes") : (ar ? "لا" : "No"));
                    kvRowIf(rt, ar, "Lessons learned", "الدروس المستفادة", c.getPirLessons());
                    kvRowIf(rt, ar, "Follow-up actions", "إجراءات المتابعة", c.getPirFollowUp());
                    kvRowIf(rt, ar, "Reviewed by", "روجعت بواسطة", c.getPirReviewedBy());
                    if (c.getPirReviewedAt() != null)
                        kvRow(rt, ar, "Reviewed at", "تاريخ المراجعة", c.getPirReviewedAt().toString());
                    doc.add(rt);
                }
            },
            false); // portrait
    }

    // ── S8d / CE-14b: the CAB decision register ─────────────────────────────────────────────────
    //
    // The third and last artefact G-14 names. Rows come from ChangeDecisionRegisterService, whose
    // javadoc records why they carry two axes: eleven changes in dev have passed a decision point and
    // only ONE names its author, so a register can neither print one row nor print eleven attributed
    // ones. Both renderers below print "not recorded" where nothing was recorded — never a blank cell,
    // because in a decision register a blank reads as "no objection".

    /** Localised label for a basis/evidence code. Kept here so both renderers agree. */
    private static String decisionAxisLabel(String code, boolean ar) {
        return switch (code == null ? "" : code) {
            case ChangeDecisionRegisterService.BASIS_RECORDED   -> ar ? "مُسجَّل" : "Recorded";
            case ChangeDecisionRegisterService.BASIS_INFERRED   -> ar ? "مُستنتج من الحالة" : "Inferred from state";
            case ChangeDecisionRegisterService.EVIDENCE_RESOLUTION -> ar ? "قرار لجنة" : "Committee resolution";
            case ChangeDecisionRegisterService.EVIDENCE_VOTES   -> ar ? "أصوات مُسجَّلة" : "Recorded votes";
            case ChangeDecisionRegisterService.EVIDENCE_NONE    -> ar ? "لا يوجد" : "None";
            default -> code == null ? "" : code;
        };
    }

    private static final String NOT_RECORDED_EN = "not recorded";
    private static final String NOT_RECORDED_AR = "غير مُسجَّل";

    @Transactional(readOnly = true)
    public byte[] changeDecisionRegisterPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);
        String none = ar ? NOT_RECORDED_AR : NOT_RECORDED_EN;

        ChangeDecisionRegisterService.Register reg = decisionRegisterService.build();
        ExportAuditContext.record(reg.rows().size(), 1, "ExportService.changeDecisionRegister");

        long attributed = reg.attributed();
        long resolutionBacked = reg.byEvidence()
            .getOrDefault(ChangeDecisionRegisterService.EVIDENCE_RESOLUTION, 0L);
        long unevidenced = reg.byEvidence()
            .getOrDefault(ChangeDecisionRegisterService.EVIDENCE_NONE, 0L);

        return buildReportPdf(o, ar,
            "Change Management", "إدارة التغيير",
            "CAB Decision Register", "سجل قرارات لجنة استشارات التغيير",
            List.of(
                new PdfTheme.Kpi(ar ? "القرارات" : "Decisions", String.valueOf(reg.total()),
                    ar ? "تجاوزت نقطة قرار" : "Past a decision point"),
                // ⚠ This KPI is the whole point of the document. "12 decisions" alone would let a
                // reader assume twelve recorded acts; eleven of them name nobody.
                new PdfTheme.Kpi(ar ? "منسوبة" : "Attributed", attributed + " / " + reg.total(),
                    ar ? "تحمل وقتاً أو صاحب قرار" : "Carry a time or an author"),
                new PdfTheme.Kpi(ar ? "بقرار لجنة" : "By resolution", String.valueOf(resolutionBacked),
                    ar ? "سجل النظام" : "System of record"),
                new PdfTheme.Kpi(ar ? "بلا إثبات" : "Unevidenced", String.valueOf(unevidenced),
                    ar ? "لا قرار ولا أصوات" : "No resolution, no votes")
            ),
            new ReportMetadata("Change", ar ? "سجل قرارات CAB" : "CAB Decision Register",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(java.time.LocalDate.now()),
                ar ? "كل تغيير تجاوز نقطة قرار" : "Every change past a decision point",
                lang, "Internal", reg.rows().size()),
            (doc) -> {
                float[] widths = new float[]{ 1.3f, 3.2f, 1.3f, 1.5f, 1.5f, 1.6f, 1.5f, 1.3f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                t.setRunDirection(PdfTheme.runDir(ar));
                String[] hdr = ar
                    ? new String[]{ "الرمز", "التغيير", "القرار", "التاريخ", "صاحب القرار",
                                    "الأساس", "الإثبات", "الشروط" }
                    : new String[]{ "Code", "Change", "Decision", "Decided", "By",
                                    "Basis", "Evidence", "Conditions" };
                for (String h : hdr) headerCell(t, h);
                for (ChangeDecisionRegisterService.DecisionRow r : reg.rows()) {
                    String name = ar && r.titleAr() != null ? r.titleAr() : r.titleEn();
                    cell(t, safe(r.reference()));
                    cell(t, safe(name));
                    cell(t, safe(r.decision()));
                    cell(t, r.decidedAt() != null ? DATE_FMT.format(r.decidedAt().toLocalDate()) : none);
                    cell(t, r.decidedBy() != null && !r.decidedBy().isBlank() ? r.decidedBy() : none);
                    cell(t, decisionAxisLabel(r.basis(), ar));
                    // The resolution code earns its place beside the evidence label: it is what an
                    // auditor asks for next.
                    cell(t, decisionAxisLabel(r.evidence(), ar)
                        + (r.resolutionCode() != null ? " · " + r.resolutionCode() : "")
                        + (r.voteSummary() != null && !r.voteSummary().isEmpty() && r.resolutionCode() == null
                            ? " · " + r.voteSummary() : ""));
                    cell(t, r.conditionsTotal() == 0 ? "—"
                        : r.conditionsOpen() + " / " + r.conditionsTotal());
                }
                doc.add(t);

                // Recusals are a governance fact, not a footnote — but only a handful of rows have
                // them, so they go under the table rather than into a column that is empty for all
                // but one row.
                List<ChangeDecisionRegisterService.DecisionRow> withRecusals = reg.rows().stream()
                    .filter(r -> r.recusals() != null && !r.recusals().isEmpty()).toList();
                if (!withRecusals.isEmpty()) {
                    cabSection(doc, ar, "Recusals", "التنحّي عن التصويت");
                    for (ChangeDecisionRegisterService.DecisionRow r : withRecusals) {
                        Paragraph p = new Paragraph(safe(r.reference()) + " — "
                            + String.join(", ", r.recusals()), PdfFonts.body(9));
                        if (ar) p.setAlignment(Element.ALIGN_RIGHT);
                        doc.add(p);
                    }
                }
            });
    }

    @Transactional(readOnly = true)
    public byte[] changeDecisionRegisterXlsx(String lang) {
        boolean ar = "ar".equalsIgnoreCase(lang);
        String none = ar ? NOT_RECORDED_AR : NOT_RECORDED_EN;
        ChangeDecisionRegisterService.Register reg = decisionRegisterService.build();
        ExportAuditContext.record(reg.rows().size(), 1, "ExportService.changeDecisionRegister");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("CAB Decisions");
            CellStyle hdrStyle = headerStyle(wb);
            CellStyle wrapStyle = wrapStyle(wb);

            String[] headers = { "Code", "Title (EN)", "Title (AR)", "Type", "Risk", "Status",
                "Decision", "Decided At", "Decided By", "Basis", "Evidence", "Resolution",
                "Resolution Outcome", "Tally Source", "For", "Against", "Abstain", "Recused",
                "Recorded Votes", "Conditions Open", "Conditions Total", "Decision Notes" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }

            int rowIdx = 1;
            for (ChangeDecisionRegisterService.DecisionRow r : reg.rows()) {
                Row row = sheet.createRow(rowIdx++);
                int i = 0;
                row.createCell(i++).setCellValue(safe(r.reference()));
                Cell t1 = row.createCell(i++); t1.setCellValue(safe(r.titleEn())); t1.setCellStyle(wrapStyle);
                Cell t2 = row.createCell(i++); t2.setCellValue(safe(r.titleAr())); t2.setCellStyle(wrapStyle);
                row.createCell(i++).setCellValue(safe(r.changeType()));
                row.createCell(i++).setCellValue(safe(r.riskLevel()));
                row.createCell(i++).setCellValue(safe(r.status()));
                row.createCell(i++).setCellValue(safe(r.decision()));
                row.createCell(i++).setCellValue(r.decidedAt() != null ? r.decidedAt().toString() : none);
                row.createCell(i++).setCellValue(
                    r.decidedBy() != null && !r.decidedBy().isBlank() ? r.decidedBy() : none);
                row.createCell(i++).setCellValue(safe(r.basis()));
                row.createCell(i++).setCellValue(safe(r.evidence()));
                row.createCell(i++).setCellValue(safe(r.resolutionCode()));
                row.createCell(i++).setCellValue(safe(r.resolutionOutcome()));
                // S7f's lesson: a tally with no stated source is a figure somebody typed.
                row.createCell(i++).setCellValue(safe(r.tallySource()));
                row.createCell(i++).setCellValue(r.voteFor() == null ? "" : String.valueOf(r.voteFor()));
                row.createCell(i++).setCellValue(r.voteAgainst() == null ? "" : String.valueOf(r.voteAgainst()));
                row.createCell(i++).setCellValue(r.voteAbstain() == null ? "" : String.valueOf(r.voteAbstain()));
                row.createCell(i++).setCellValue(
                    r.recusals() == null || r.recusals().isEmpty() ? "" : String.join(", ", r.recusals()));
                row.createCell(i++).setCellValue(safe(r.voteSummary()));
                row.createCell(i++).setCellValue(String.valueOf(r.conditionsOpen()));
                row.createCell(i++).setCellValue(String.valueOf(r.conditionsTotal()));
                Cell nc = row.createCell(i); nc.setCellValue(safe(r.decisionNotes())); nc.setCellStyle(wrapStyle);
            }

            XlsxStyler.freezeAndFilter(sheet, headers.length, rowIdx - 1);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(sheet, i, 1, rowIdx - 1);
            }
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    // ── S7f / CE-14a: the committee meeting's agenda pack and its minutes ───────────────────────
    //
    // G-14: "No agenda pack / minutes / decision-register exports for change governance." The CAB
    // pack above is a pack for ONE CHANGE (CH-11); these are the MEETING's records — the artifacts a
    // secretary circulates before a sitting and files after it. Measured 2026-08-04: the committee
    // module had **no export at all** beyond a blank form template, while holding 34 meetings in dev
    // and 32 in prod, 16/15 of them MINUTES_APPROVED with agenda and decision content.
    //
    // ⚠ THE HONESTY REQUIREMENT, AND IT IS THE POINT OF THE SLICE. An export is where a governance
    // record becomes evidence, so it must not upgrade an assertion into a computation. Measured
    // across both databases:
    //
    //     quorum_source        15 NULL / 17 ASSERTED_LEGACY / 2 COMPUTED  (dev), 0 COMPUTED in prod
    //     quorum_present       populated on 2 of 34 (dev), 0 of 32 (prod)
    //     tally_source         20 ASSERTED_LEGACY / 2 COMPUTED (dev), 0 COMPUTED in prod
    //     resolution_vote      4 rows (dev), 0 (prod)
    //
    // So a minutes PDF printing "Quorum: 4 of 7, required 4" would be inventing numbers for 32 of 34
    // meetings, and printing "For 0 / Against 0" would read as unanimous abstention where in truth
    // no per-member vote was ever recorded. Both are the F2-42 shape — a page that presents computed
    // confidence over data that does not support it. F2-28 added `quorum_source` and S-era work added
    // `tally_source` precisely so the distinction can be stated; this export states it.

    /**
     * CE-14a — the agenda pack for one committee meeting (RTL + bilingual).
     *
     * <p>Built from the meeting's own `agenda_json` snapshot, which is what S7b's `populateAgenda`
     * writes and what the sitting is actually run from. Deliberately a snapshot read rather than a
     * live re-derivation: an agenda pack records what was tabled, and re-deriving it at export time
     * would produce a document that disagrees with the one the members received.
     */
    @Transactional(readOnly = true)
    public byte[] committeeAgendaPdf(Long meetingId, String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);
        CommitteeMeeting m = meetingRepo.findByIdAndOrganisationId(meetingId, org)
            .orElseThrow(() -> new EntityNotFoundException("Committee meeting not found: " + meetingId));
        List<Map<String, Object>> items = readJsonArray(m.getAgendaJson());
        String title = ar && !isBlank(m.getTitleAr()) ? m.getTitleAr() : m.getTitleEn();

        return buildReportPdf(o, ar,
            "Committee Agenda Pack", "حزمة جدول أعمال اللجنة",
            (ar ? "جدول الأعمال — " : "Agenda — ") + safe(m.getMeetingCode()),
            (ar ? "جدول الأعمال — " : "Agenda — ") + safe(m.getMeetingCode()),
            List.of(
                new PdfTheme.Kpi(ar ? "اللجنة" : "Committee", safe(m.getCommitteeNameEn()), ""),
                new PdfTheme.Kpi(ar ? "الحالة" : "Status",    safe(m.getStatus()), ""),
                new PdfTheme.Kpi(ar ? "البنود" : "Items",     String.valueOf(items.size()), ""),
                new PdfTheme.Kpi(ar ? "الموعد" : "Scheduled",
                    m.getScheduledAt() == null ? "—" : m.getScheduledAt().toString(), "")
            ),
            new ReportMetadata("Committee", ar ? "حزمة جدول الأعمال" : "Agenda Pack",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(LocalDate.now()),
                safe(m.getMeetingCode()), lang, "Internal", 1),
            (doc) -> {
                PdfPTable bar = fullTable(ar);
                headerCell(bar, safe(title));
                doc.add(bar);

                cabSection(doc, ar, "Meeting", "الاجتماع");
                PdfPTable d = kvTable(ar);
                kvRow(d, ar, "Committee", "اللجنة", safe(m.getCommitteeNameEn()));
                kvRow(d, ar, "Meeting code", "رمز الاجتماع", safe(m.getMeetingCode()));
                kvRow(d, ar, "Scheduled", "الموعد",
                    m.getScheduledAt() == null ? "—" : m.getScheduledAt().toString());
                kvRow(d, ar, "Location / link", "المكان أو الرابط", safe(m.getLocationOrLink()));
                kvRow(d, ar, "Chair", "الرئيس", safe(m.getChairName()));
                kvRow(d, ar, "Secretary", "أمين السر", safe(m.getSecretaryName()));
                kvRow(d, ar, "Status", "الحالة", safe(m.getStatus()));
                doc.add(d);

                cabSection(doc, ar, "Agenda", "جدول الأعمال");
                if (items.isEmpty()) {
                    // Stated, not blank. An agenda pack with an empty item table reads as "a meeting
                    // with nothing to discuss"; the truth is that the agenda has not been built, and
                    // 2 of 34 dev meetings are in exactly that state.
                    PdfPTable none = kvTable(ar);
                    kvRow(none, ar, "—", "—", ar
                        ? "لم يُبنَ جدول الأعمال لهذا الاجتماع بعد."
                        : "The agenda for this meeting has not been built yet.");
                    doc.add(none);
                } else {
                    PdfPTable t = new PdfPTable(new float[]{ 0.7f, 4.6f, 1.4f, 1.1f });
                    t.setWidthPercentage(100);
                    t.setRunDirection(PdfTheme.runDir(ar));
                    t.setSpacingBefore(4);
                    headerCell(t, ar ? "#" : "#");
                    headerCell(t, ar ? "البند" : "Item");
                    headerCell(t, ar ? "النوع" : "Type");
                    headerCell(t, ar ? "الدقائق" : "Minutes");
                    for (Map<String, Object> it : items) {
                        cell(t, str(it.get("seq")));
                        cell(t, safe(agendaItemText(it, ar)));
                        // S7b's referral items carry a kind/mandatory marker; standing items do not,
                        // so an absent marker is rendered as standing rather than as unknown.
                        cell(t, itemType(it, ar));
                        cell(t, str(firstNonBlankStr(it, "minutesAllocated", "minutes")));
                    }
                    doc.add(t);
                }
            },
            false); // portrait — a circulated pack, not a register dump
    }

    /**
     * CE-14a — the minutes of one committee meeting (RTL + bilingual), including the quorum and
     * tally <em>provenance</em>.
     *
     * <p>Every figure that could be either computed or asserted is labelled with which it was. That
     * is not decoration: `quorum_source` is `ASSERTED_LEGACY` on 17 of 34 dev meetings and NULL on
     * 15 more, so a minutes document that printed the numbers plainly would be presenting a
     * caller-authored claim as a measured fact — the defect F2-28 exists to have made visible.
     */
    @Transactional(readOnly = true)
    public byte[] committeeMinutesPdf(Long meetingId, String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);
        CommitteeMeeting m = meetingRepo.findByIdAndOrganisationId(meetingId, org)
            .orElseThrow(() -> new EntityNotFoundException("Committee meeting not found: " + meetingId));
        List<Map<String, Object>> attendees = readJsonArray(m.getAttendeesJson());
        List<Map<String, Object>> actions   = readJsonArray(m.getActionItemsJson());
        List<CommitteeResolution> resolutions =
            resolutionRepo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(org, meetingId);
        long present = attendees.stream().filter(a -> Boolean.TRUE.equals(a.get("present"))).count();
        String title = ar && !isBlank(m.getTitleAr()) ? m.getTitleAr() : m.getTitleEn();
        boolean approved = "MINUTES_APPROVED".equals(m.getStatus());

        return buildReportPdf(o, ar,
            "Committee Minutes", "محضر اجتماع اللجنة",
            (ar ? "المحضر — " : "Minutes — ") + safe(m.getMeetingCode()),
            (ar ? "المحضر — " : "Minutes — ") + safe(m.getMeetingCode()),
            List.of(
                new PdfTheme.Kpi(ar ? "اللجنة" : "Committee", safe(m.getCommitteeNameEn()), ""),
                new PdfTheme.Kpi(ar ? "الحضور" : "Present",
                    attendees.isEmpty() ? "—" : present + "/" + attendees.size(), ""),
                new PdfTheme.Kpi(ar ? "القرارات" : "Resolutions",
                    String.valueOf(resolutions.size()), ""),
                // The KPI band states approval, never assumes it — an unapproved minute is a draft,
                // and a document that looks approved because it was exported is the F2-98 shape.
                new PdfTheme.Kpi(ar ? "الاعتماد" : "Approval",
                    approved ? (ar ? "معتمد" : "Approved") : (ar ? "مسوّدة" : "Draft"),
                    approved ? "" : (ar ? "غير معتمد" : "not approved"))
            ),
            new ReportMetadata("Committee", ar ? "محضر الاجتماع" : "Minutes",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(LocalDate.now()),
                safe(m.getMeetingCode()), lang, "Internal", 1),
            (doc) -> {
                PdfPTable bar = fullTable(ar);
                headerCell(bar, safe(title));
                doc.add(bar);

                if (!approved) {
                    // Say it on the page, not only in the KPI band. Minutes circulate as files.
                    PdfPTable warn = fullTable(ar);
                    cell(warn, ar
                        ? "مسوّدة — لم يُعتمد هذا المحضر بعد (الحالة: " + safe(m.getStatus()) + ")."
                        : "DRAFT — these minutes have not been approved (status: "
                            + safe(m.getStatus()) + ").");
                    doc.add(warn);
                }

                cabSection(doc, ar, "Meeting", "الاجتماع");
                PdfPTable d = kvTable(ar);
                kvRow(d, ar, "Committee", "اللجنة", safe(m.getCommitteeNameEn()));
                kvRow(d, ar, "Meeting code", "رمز الاجتماع", safe(m.getMeetingCode()));
                kvRow(d, ar, "Held", "تاريخ الانعقاد",
                    m.getStartedAt() != null ? m.getStartedAt().toString()
                        : m.getScheduledAt() == null ? "—" : m.getScheduledAt().toString());
                kvRow(d, ar, "Chair", "الرئيس", safe(m.getChairName()));
                kvRow(d, ar, "Secretary", "أمين السر", safe(m.getSecretaryName()));
                doc.add(d);

                // ── Attendance and quorum, with provenance ──────────────────────────────────────
                cabSection(doc, ar, "Attendance & Quorum", "الحضور والنصاب");
                if (attendees.isEmpty()) {
                    PdfPTable none = kvTable(ar);
                    kvRow(none, ar, "—", "—", ar
                        ? "لم يُسجَّل حضور لهذا الاجتماع."
                        : "No attendance was recorded for this meeting.");
                    doc.add(none);
                } else {
                    PdfPTable t = new PdfPTable(new float[]{ 3.4f, 1.8f, 1.1f, 1.3f });
                    t.setWidthPercentage(100);
                    t.setRunDirection(PdfTheme.runDir(ar));
                    t.setSpacingBefore(4);
                    headerCell(t, ar ? "العضو" : "Member");
                    headerCell(t, ar ? "الصفة" : "Role");
                    headerCell(t, ar ? "حاضر" : "Present");
                    headerCell(t, ar ? "له صوت" : "Voting");
                    for (Map<String, Object> a : attendees) {
                        cell(t, safe(str(a.get("name"))));
                        cell(t, safe(str(a.get("role"))));
                        cell(t, yesNo(a.get("present"), ar));
                        cell(t, yesNo(a.get("votingMember"), ar));
                    }
                    doc.add(t);
                }
                PdfPTable q = kvTable(ar);
                kvRow(q, ar, "Quorum met", "تحقق النصاب",
                    m.getQuorumMet() == null ? "—"
                        : (m.getQuorumMet() != 0 ? (ar ? "نعم" : "Yes") : (ar ? "لا" : "No")));
                // ⚠ The provenance line, and the figures ONLY where they were recorded.
                kvRow(q, ar, "Quorum basis", "أساس النصاب", quorumBasis(m, ar));
                if (m.getQuorumPresent() != null || m.getQuorumRequired() != null
                        || m.getQuorumDenominator() != null) {
                    kvRow(q, ar, "Counted", "المحتسب",
                        str(m.getQuorumPresent()) + " / " + str(m.getQuorumDenominator())
                            + (ar ? "  (المطلوب " : "  (required ") + str(m.getQuorumRequired()) + ")");
                    if (m.getQuorumComputedAt() != null)
                        kvRow(q, ar, "Computed at", "وقت الحساب", m.getQuorumComputedAt().toString());
                }
                if (m.getAttendanceRecordedAt() != null)
                    kvRow(q, ar, "Attendance recorded", "تسجيل الحضور",
                        m.getAttendanceRecordedAt().toString());
                doc.add(q);

                // ── Resolutions, with tally provenance ─────────────────────────────────────────
                cabSection(doc, ar, "Resolutions", "القرارات");
                if (resolutions.isEmpty()) {
                    PdfPTable none = kvTable(ar);
                    kvRow(none, ar, "—", "—", ar
                        ? "لم تُسجَّل قرارات لهذا الاجتماع."
                        : "No resolutions were recorded for this meeting.");
                    doc.add(none);
                } else {
                    PdfPTable t = new PdfPTable(new float[]{ 0.7f, 3.6f, 1.3f, 1.5f, 1.6f });
                    t.setWidthPercentage(100);
                    t.setRunDirection(PdfTheme.runDir(ar));
                    t.setSpacingBefore(4);
                    headerCell(t, "#");
                    headerCell(t, ar ? "القرار" : "Resolution");
                    headerCell(t, ar ? "النتيجة" : "Outcome");
                    headerCell(t, ar ? "الأصوات" : "Votes");
                    headerCell(t, ar ? "أساس العد" : "Tally basis");
                    for (CommitteeResolution r : resolutions) {
                        cell(t, str(r.getSeq()));
                        cell(t, safe(ar && !isBlank(r.getTitleAr()) ? r.getTitleAr() : r.getTitleEn()));
                        cell(t, safe(r.getOutcome()));
                        cell(t, voteSummary(r, ar));
                        cell(t, tallyBasis(r, ar));
                    }
                    doc.add(t);
                    for (CommitteeResolution r : resolutions) {
                        String dissent = ar ? r.getDissentNotesAr() : r.getDissentNotesEn();
                        if (isBlank(dissent)) dissent = r.getDissentNotesEn();
                        if (isBlank(dissent)) continue;
                        // A recorded dissent is the part of a minute an auditor reads first; losing
                        // it to a summary table would be the point of the document going missing.
                        PdfPTable dt = kvTable(ar);
                        kvRow(dt, ar, "Dissent · #" + str(r.getSeq()),
                              "اعتراض · #" + str(r.getSeq()), dissent);
                        doc.add(dt);
                    }
                }

                // ── Action items ──────────────────────────────────────────────────────────────
                if (!actions.isEmpty()) {
                    cabSection(doc, ar, "Action Items", "بنود العمل");
                    PdfPTable t = new PdfPTable(new float[]{ 4.2f, 1.8f, 1.4f });
                    t.setWidthPercentage(100);
                    t.setRunDirection(PdfTheme.runDir(ar));
                    t.setSpacingBefore(4);
                    headerCell(t, ar ? "البند" : "Action");
                    headerCell(t, ar ? "المسؤول" : "Owner");
                    headerCell(t, ar ? "الاستحقاق" : "Due");
                    for (Map<String, Object> a : actions) {
                        cell(t, safe(firstNonBlankStr(a, ar ? "titleAr" : "titleEn", "titleEn", "title")));
                        cell(t, safe(firstNonBlankStr(a, "owner", "ownerName", "assignedTo")));
                        cell(t, safe(firstNonBlankStr(a, "dueAt", "dueDate", "due")));
                    }
                    doc.add(t);
                }

                // ── The narrative ─────────────────────────────────────────────────────────────
                String narrative = ar && !isBlank(m.getMinutesAr()) ? m.getMinutesAr() : m.getMinutesEn();
                cabSection(doc, ar, "Minutes", "نص المحضر");
                PdfPTable n = fullTable(ar);
                cell(n, isBlank(narrative)
                    ? (ar ? "لم يُسجَّل نص للمحضر." : "No minutes narrative was recorded.")
                    : narrative);
                doc.add(n);

                // ── Approval + e-signature ────────────────────────────────────────────────────
                cabSection(doc, ar, "Approval", "الاعتماد");
                PdfPTable ap = kvTable(ar);
                kvRow(ap, ar, "Status", "الحالة", safe(m.getStatus()));
                kvRow(ap, ar, "Approved at", "تاريخ الاعتماد",
                    m.getMinutesApprovedAt() == null ? "—" : m.getMinutesApprovedAt().toString());
                kvRow(ap, ar, "Approved by", "المعتمد", safe(m.getApproverName()));
                doc.add(ap);
            },
            false); // portrait
    }

    // ── S7f helpers: JSON snapshots and provenance wording ──────────────────────────────────────

    /** Read a `*_json` snapshot column into rows. A malformed snapshot yields no rows rather than a
     *  500: the export must still produce the rest of the minutes, and the empty section states
     *  itself. */
    private List<Map<String, Object>> readJsonArray(String json) {
        if (isBlank(json)) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            log.warn("S7f: unreadable JSON snapshot in a committee meeting export — rendering the "
                + "section as absent rather than failing the document: {}", e.getMessage());
            return List.of();
        }
    }

    private static String str(Object o) { return o == null ? "—" : String.valueOf(o); }

    private static String yesNo(Object o, boolean ar) {
        if (o == null) return "—";
        boolean v = Boolean.TRUE.equals(o) || "true".equalsIgnoreCase(String.valueOf(o));
        return v ? (ar ? "نعم" : "Yes") : (ar ? "لا" : "No");
    }

    /**
     * ⚠ CE-13 — this read the {@code titleEn}/{@code titleAr} keys only, and the platform writes the
     * item title under <b>two</b> key shapes.
     *
     * <p>Measured across all 34 dev meetings: 24 agendas — every real committee, both quarters — key
     * their items {@code titleEn}, and the 8 older DEMO ones plus <b>everything
     * {@code populateAgenda} builds</b> key them {@code topicEn}. So this pack printed {@code —} for
     * exactly the agendas S7b taught to carry the CAB queue, while the frontend's agenda list (which
     * reads {@code topicEn}) printed empty bullets for the other 24. Both readers were wrong, in
     * opposite directions, and S7f's probe passed because the meeting it exported was a seeded one.
     *
     * <p>Now delegated to {@link CommitteeAgendaJson} so there is one reader, and it accepts either
     * shape. The same fault ran one field over — {@code isMandatory} vs {@code mandatory} in
     * {@link #itemType}, and {@code minutesAllocated} vs {@code minutes} in the row builder.
     */
    private static String agendaItemText(Map<String, Object> item, boolean ar) {
        String t = CommitteeAgendaJson.topic(item, ar);
        return t == null ? "—" : t;
    }

    private static String itemType(Map<String, Object> item, boolean ar) {
        if (CommitteeAgendaJson.mandatory(item)) return ar ? "إلزامي" : "Mandatory";
        return ar ? "قياسي" : "Standing";
    }

    private static String firstNonBlankStr(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return null;
    }

    /**
     * How the quorum was established — the sentence that stops this export laundering an assertion
     * into evidence (F2-28's {@code quorum_source} is what makes it sayable).
     */
    private static String quorumBasis(CommitteeMeeting m, boolean ar) {
        String src = m.getQuorumSource();
        if (isBlank(src))
            return ar ? "غير مسجَّل" : "Not recorded";
        return switch (src) {
            case "COMPUTED" -> ar ? "محسوب من سجل الأعضاء" : "Computed from the member roster";
            case "ASSERTED_LEGACY" -> ar
                ? "مُقرَّر من قِبل المُدخِل (سجل قديم) — غير محسوب"
                : "Asserted by the recorder (legacy row) — not computed";
            default -> src;
        };
    }

    /**
     * The vote summary, and it refuses to print zeros as a tally. Measured: 20 of 22 resolutions in
     * dev and 20 of 20 in prod carry {@code tally_source = ASSERTED_LEGACY}, and
     * {@code committee_resolution_vote} holds 4 rows in dev and 0 in prod — so "For 0 / Against 0"
     * would read as unanimous abstention where no vote was ever recorded.
     */
    private static String voteSummary(CommitteeResolution r, boolean ar) {
        int f = r.getVoteFor() == null ? 0 : r.getVoteFor();
        int a = r.getVoteAgainst() == null ? 0 : r.getVoteAgainst();
        int b = r.getVoteAbstain() == null ? 0 : r.getVoteAbstain();
        if (f == 0 && a == 0 && b == 0)
            return ar ? "لم تُسجَّل أصوات" : "No votes recorded";
        return (ar ? "مع " : "For ") + f + (ar ? " · ضد " : " · Against ") + a
             + (ar ? " · ممتنع " : " · Abstain ") + b;
    }

    private static String tallyBasis(CommitteeResolution r, boolean ar) {
        String src = r.getTallySource();
        if (isBlank(src)) return ar ? "غير مسجَّل" : "Not recorded";
        return switch (src) {
            case "COMPUTED" -> ar ? "محسوب من الأصوات" : "Computed from recorded votes";
            case "ASSERTED_LEGACY" -> ar ? "مُقرَّر (سجل قديم)" : "Asserted (legacy)";
            default -> src;
        };
    }

    // ── CAB-pack layout helpers ─────────────────────────────────────────────
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static PdfPTable fullTable(boolean ar) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfTheme.runDir(ar));
        return t;
    }
    private static PdfPTable kvTable(boolean ar) {
        PdfPTable t = new PdfPTable(new float[]{ 1.6f, 4f });
        t.setWidthPercentage(100);
        t.setRunDirection(PdfTheme.runDir(ar));
        t.setSpacingBefore(4);
        return t;
    }
    private static void kvRow(PdfPTable t, boolean ar, String labelEn, String labelAr, String value) {
        headerCell(t, ar ? labelAr : labelEn);
        cell(t, value);
    }
    private static void kvRowIf(PdfPTable t, boolean ar, String labelEn, String labelAr, String value) {
        if (!isBlank(value)) kvRow(t, ar, labelEn, labelAr, value);
    }
    private void cabSection(Document doc, boolean ar, String en, String arT) throws DocumentException {
        Paragraph p = new Paragraph(ar ? arT : en, PdfFonts.header(11));
        p.setSpacingBefore(12);
        p.setSpacingAfter(2);
        if (ar) p.setAlignment(Element.ALIGN_RIGHT);
        doc.add(p);
    }

    // ── CH-10: controlled policy document (renders inline content_md to a PDF) ──
    //
    // The controlled-output side of the CH-10 structured editor: takes a policy's
    // authored content_md (markdown-ish: #/##/### headings, - bullets, paragraphs)
    // and renders a branded, portrait, RTL-aware document. Every line goes through
    // a borderless full-width table so Arabic content shapes + mirrors exactly like
    // the rest of the platform's exports.

    @Transactional(readOnly = true)
    public byte[] policyDocumentPdf(Long policyId, String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);
        PolicyDocument p = policyDocRepo.findByIdAndOrganisationId(policyId, org)
            .orElseThrow(() -> new EntityNotFoundException("Policy document not found: " + policyId));
        String title = ar && p.getTitleAr() != null ? p.getTitleAr() : p.getTitleEn();
        String content = p.getContentMd() != null ? p.getContentMd() : "";

        return buildReportPdf(o, ar,
            "Policy Document", "وثيقة سياسة",
            safe(title) + "  ·  v" + safe(p.getVersion()),
            safe(title) + "  ·  v" + safe(p.getVersion()),
            List.of(
                new PdfTheme.Kpi(ar ? "الإصدار" : "Version",  safe(p.getVersion()),  ""),
                new PdfTheme.Kpi(ar ? "الحالة"  : "Status",   safe(p.getStatus()),   ""),
                new PdfTheme.Kpi(ar ? "الفئة"   : "Category", safe(p.getCategory()), ""),
                new PdfTheme.Kpi(ar ? "السريان" : "Effective",
                    p.getEffectiveDate() != null ? p.getEffectiveDate().toString() : "—", "")
            ),
            new ReportMetadata("Policy", ar ? "وثيقة سياسة" : "Policy Document",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(LocalDate.now()),
                safe(p.getPolicyCode()) + " v" + safe(p.getVersion()), lang, "Internal", 1),
            (doc) -> renderPolicyContent(doc, content, ar),
            false); // portrait
    }

    private void renderPolicyContent(Document doc, String content, boolean ar) throws DocumentException {
        if (content == null || content.isBlank()) {
            Paragraph none = new Paragraph(ar ? "لا يوجد محتوى مُدرج بعد." : "No inline content authored yet.",
                PdfFonts.body(11));
            if (ar) none.setAlignment(Element.ALIGN_RIGHT);
            doc.add(none);
            return;
        }
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfTheme.runDir(ar));
        for (String raw : content.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty())                 contentCell(t, " ",                    PdfFonts.body(6),   ar, 2, 0);
            else if (line.startsWith("### "))   contentCell(t, line.substring(4),      PdfFonts.header(11), ar, 6, 0);
            else if (line.startsWith("## "))    contentCell(t, line.substring(3),      PdfFonts.header(13), ar, 8, 0);
            else if (line.startsWith("# "))     contentCell(t, line.substring(2),      PdfFonts.header(15), ar, 10, 0);
            else if (line.startsWith("- ") || line.startsWith("* "))
                                                contentCell(t, "•  " + line.substring(2), PdfFonts.body(10), ar, 2, 14);
            else                                contentCell(t, line,                   PdfFonts.body(10),  ar, 3, 0);
        }
        doc.add(t);
    }

    private static void contentCell(PdfPTable t, String text, com.lowagie.text.Font font,
                                    boolean ar, float topPad, float indent) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(PdfPCell.NO_BORDER);
        c.setPaddingTop(topPad);
        c.setPaddingBottom(1);
        if (t.getRunDirection() == PdfWriter.RUN_DIRECTION_RTL) {
            c.setHorizontalAlignment(PdfTheme.visualStart(true, PdfWriter.RUN_DIRECTION_RTL));
            c.setPaddingRight(indent);
        } else {
            c.setPaddingLeft(indent);
        }
        t.addCell(c);
    }

    // ── Findings aging ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] findingsAgingPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        ExportAuditContext.record(findings.size(), 1, "ExportService.findingsAging");   // F2-41
        LocalDate today = LocalDate.now();

        // KPI counts — total, open, past-30d, past-90d (escalation tier triggers)
        long total = findings.size();
        long open  = findings.stream().filter(f -> !"CLOSED".equalsIgnoreCase(f.getStatus())).count();
        long past30 = 0, past90 = 0;
        for (AuditFinding f : findings) {
            if ("CLOSED".equalsIgnoreCase(f.getStatus())) continue;
            LocalDateTime created = f.getCreatedAt();
            long days = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
            if (days >= 90) past90++;
            else if (days >= 30) past30++;
        }
        final long past30Final = past30;
        final long past90Final = past90;

        return buildReportPdf(o, ar,
            "Audit Findings",                "ملاحظات التدقيق",
            "Findings Aging Report",         "تقرير تقادم الملاحظات",
            List.of(
                new PdfTheme.Kpi(ar ? "إجمالي" : "Total",     String.valueOf(total), ""),
                new PdfTheme.Kpi(ar ? "مفتوحة" : "Open",      String.valueOf(open),  ""),
                new PdfTheme.Kpi(ar ? "متجاوزة 30 يوم" : "Past 30d", String.valueOf(past30Final), ar ? "تصعيد مدير" : "Manager tier"),
                new PdfTheme.Kpi(ar ? "متجاوزة 90 يوم" : "Past 90d", String.valueOf(past90Final), ar ? "تصعيد تنفيذي" : "Exec tier")
            ),
            new ReportMetadata("Compliance", ar ? "تقرير تقادم الملاحظات" : "Findings Aging Report",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(java.time.LocalDate.now()),
                ar ? "جميع الملاحظات (بحد أقصى 1000)" : "All findings (max 1000)", lang, "Internal", findings.size()),
            (doc) -> {
                float[] widths = new float[]{ 4f, 1.4f, 1.2f, 1f, 1.4f, 1.6f, 1.4f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                t.setRunDirection(PdfTheme.runDir(ar));
                String[] hdr = ar
                    ? new String[]{ "الملاحظة", "الأهمية", "المالك", "الأيام", "التصعيد", "مرحلة CAPA", "الحالة" }
                    : new String[]{ "Finding",  "Severity", "Owner",  "Days",   "Tier",     "CAPA Stage",  "Status" };
                for (String h : hdr) headerCell(t, h);

                for (AuditFinding f : findings) {
                    LocalDateTime created = f.getCreatedAt();
                    long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                    String tier = daysOpen >= 90 ? "EXEC" : daysOpen >= 60 ? "DIRECTOR" : daysOpen >= 30 ? "MANAGER" : "ON_TRACK";
                    cell(t, safe(f.getTitle()));
                    cell(t, safe(f.getSeverity()));
                    cell(t, safe(f.getOwner()));
                    cell(t, String.valueOf(daysOpen));
                    cell(t, tier);
                    cell(t, safe(f.getCapaStage()));
                    cell(t, safe(f.getStatus()));
                }
                doc.add(t);
            });
    }

    // ── KRI dashboard ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] kriDashboardPdf(String lang) {
        Long org = TenantContext.getCurrentTenant();
        Organisation o = orgRepo.findById(org).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);

        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(org);
        ExportAuditContext.record(kris.size(), 1, "ExportService.kriDashboard");   // F2-41

        // QUAL-BCMP-006: one query for each KRI's latest reading instead of a
        // per-KRI N+1 loop. Key present ⇒ a reading exists; value [0]=band (may
        // be null), [1]=readingValue. Mirrors findFirstByKriIdOrderByReadingAtDesc.
        final Map<Long, Object[]> latestByKri = latestReadingByKri(kris);

        // Band counts — green/amber/red/no-data — for the cover KPI strip
        long green = 0, amber = 0, red = 0, noData = 0;
        for (KriDefinition k : kris) {
            Object[] latest = latestByKri.get(k.getId());
            String band = latest == null ? null : (String) latest[0];
            if (band == null) noData++;
            else if ("GREEN".equalsIgnoreCase(band)) green++;
            else if ("AMBER".equalsIgnoreCase(band)) amber++;
            else if ("RED".equalsIgnoreCase(band)) red++;
        }
        final long greenF = green, amberF = amber, redF = red, noDataF = noData;

        return buildReportPdf(o, ar,
            "Key Risk Indicators",         "مؤشرات المخاطر الرئيسية",
            "KRI Dashboard",               "لوحة مؤشرات المخاطر",
            List.of(
                new PdfTheme.Kpi(ar ? "أخضر"  : "Green",  String.valueOf(greenF), ""),
                new PdfTheme.Kpi(ar ? "أصفر"  : "Amber",  String.valueOf(amberF), ""),
                new PdfTheme.Kpi(ar ? "أحمر"  : "Red",    String.valueOf(redF),   ""),
                new PdfTheme.Kpi(ar ? "بدون بيانات" : "No data", String.valueOf(noDataF), "")
            ),
            new ReportMetadata("ERM", ar ? "لوحة مؤشرات المخاطر" : "KRI Dashboard",
                (ar ? "حتى " : "As of ") + DATE_FMT.format(java.time.LocalDate.now()),
                ar ? "جميع المؤشرات النشطة" : "All active KRIs", lang, "Internal", kris.size()),
            (doc) -> {
                float[] widths = new float[]{ 1.3f, 4f, 1f, 1.3f, 1.3f, 1.3f, 1.6f };
                PdfPTable t = new PdfPTable(widths);
                t.setWidthPercentage(100);
                t.setRunDirection(PdfTheme.runDir(ar));
                String[] hdr = ar
                    ? new String[]{ "الرمز", "اسم المؤشر", "الحالة", "آخر قراءة", "حد أخضر", "حد أصفر", "المالك" }
                    : new String[]{ "Code",  "KRI",        "Band",    "Latest",     "Green ≤", "Amber ≤", "Owner" };
                for (String h : hdr) headerCell(t, h);

                for (KriDefinition k : kris) {
                    Object[] latest = latestByKri.get(k.getId());
                    String name = ar && k.getTitleAr() != null ? k.getTitleAr() : k.getTitleEn();
                    cell(t, safe(k.getKriCode()));
                    cell(t, safe(name));
                    cell(t, latest != null ? safe((String) latest[0]) : "—");
                    cell(t, latest != null ? ((BigDecimal) latest[1]).toPlainString() : "—");
                    cell(t, k.getGreenThreshold() != null ? k.getGreenThreshold().toPlainString() : "—");
                    cell(t, k.getAmberThreshold() != null ? k.getAmberThreshold().toPlainString() : "—");
                    cell(t, k.getOwnerUserId() != null ? String.valueOf(k.getOwnerUserId()) : "—");
                }
                doc.add(t);
            });
    }

    // ── Shared report skeleton ─────────────────────────────────────────────

    /**
     * Functional shape so each report type only has to describe its own
     * table rendering — the cover hero, KPI strip, footer, and lifecycle
     * are identical across all three reports and live here once.
     */
    @FunctionalInterface
    private interface TableRenderer {
        void render(Document doc) throws DocumentException;
    }

    private byte[] buildReportPdf(Organisation o, boolean ar,
                                  String eyebrowEn, String eyebrowAr,
                                  String titleEn, String titleAr,
                                  List<PdfTheme.Kpi> kpis,
                                  ReportMetadata meta,
                                  TableRenderer table) {
        // Register dumps are wide (code + title + L/I/score + status + …), so the
        // default is landscape; single-record narrative packs pass landscape=false.
        return buildReportPdf(o, ar, eyebrowEn, eyebrowAr, titleEn, titleAr, kpis, meta, table, true);
    }

    private byte[] buildReportPdf(Organisation o, boolean ar,
                                  String eyebrowEn, String eyebrowAr,
                                  String titleEn, String titleAr,
                                  List<PdfTheme.Kpi> kpis,
                                  ReportMetadata meta,
                                  TableRenderer table,
                                  boolean landscape) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 60, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            // P0-2: flip the whole document RTL for Arabic (was LTR-only — labels were
            // translated but layout/columns stayed left-to-right). Tables set their own
            // direction below; cells inherit from the table.
            writer.setRunDirection(PdfTheme.runDir(ar));
            writer.setPageEvent(PdfTheme.professionalFooter(eyebrowEn, "Internal"));
            doc.open();

            // Cover — carry the tenant brand colour + name override (P2 BrandTheme).
            BrandThemeService.ResolvedBrand brand = brandThemeService.resolve();
            PdfTheme.Cover cv = new PdfTheme.Cover();
            cv.eyebrow = eyebrowEn;
            cv.eyebrowAr = eyebrowAr;
            cv.titleEn = titleEn;
            cv.titleAr = titleAr;
            cv.orgNameEn = brand.orgNameEn() != null ? brand.orgNameEn() : (o == null ? null : o.getNameEn());
            cv.orgNameAr = brand.orgNameAr() != null ? brand.orgNameAr() : (o == null ? null : o.getNameAr());
            cv.brand = brand.primaryColor();
            cv.classification = "Internal";
            cv.ar = ar;   // Arabic covers lay out RTL + shaped (was LTR/unshaped)
            PdfTheme.coverHero(doc, cv);

            // KPI strip
            if (kpis != null && !kpis.isEmpty()) {
                PdfTheme.kpiStrip(doc, new ArrayList<>(kpis), ar);
            }

            // Standardized metadata block (module/type/period/scope/records/language/classification).
            if (meta != null) {
                meta.renderInto(doc, currentUsername());
            }

            // Table
            table.render(doc);

            doc.close();
            writer.close();
        } catch (DocumentException e) {
            throw new ExportException("PDF", "PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    /** Best-effort current username for the report metadata block (null → "system"). */
    private static String currentUsername() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
            return auth == null ? null : auth.getName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void headerCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.header(10)));
        c.setBackgroundColor(PdfFonts.BRAND_PRIMARY);
        c.setBorderColor(PdfFonts.HAIRLINE);
        c.setPaddingTop(6); c.setPaddingBottom(6);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        if (t.getRunDirection() == PdfWriter.RUN_DIRECTION_RTL) {
            // ALIGN semantics mirror under RTL: LEFT = reading start = visual right.
            c.setHorizontalAlignment(PdfTheme.visualStart(true, PdfWriter.RUN_DIRECTION_RTL));
        }
        t.addCell(c);
    }
    private static void cell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfFonts.body(9)));
        c.setBorderColor(PdfFonts.PAPER_BORDER);
        c.setPaddingTop(5); c.setPaddingBottom(5);
        c.setPaddingLeft(6); c.setPaddingRight(6);
        // Follow the table's direction (cells inherit shaping from the table,
        // but alignment needs the mirrored constant — see PdfTheme.visualStart).
        if (t.getRunDirection() == PdfWriter.RUN_DIRECTION_RTL) {
            c.setHorizontalAlignment(PdfTheme.visualStart(true, PdfWriter.RUN_DIRECTION_RTL));
        }
        t.addCell(c);
    }
    private static String safe(String s) { return s == null ? "—" : s; }

    /**
     * Latest reading per KRI in one query (QUAL-BCMP-006) — collapses the N+1
     * {@code findFirstByKriIdOrderByReadingAtDesc} loop shared by the KRI PDF and
     * XLSX exporters. Returns a map keyed by KRI id; presence of a key mirrors
     * {@code findFirstByKriIdOrderByReadingAtDesc(id).isPresent()}, value
     * {@code [0]=band} (nullable), {@code [1]=readingValue}.
     */
    private Map<Long, Object[]> latestReadingByKri(List<KriDefinition> kris) {
        Map<Long, Object[]> out = new HashMap<>();
        List<Long> kriIds = kris.stream().map(KriDefinition::getId).toList();
        if (kriIds.isEmpty()) return out;
        for (Object[] row : measRepo.findLatestPerKriIn(kriIds)) {
            out.put((Long) row[0], new Object[]{ row[1], row[2] });
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Excel exports (Q2) — same datasets, .xlsx with formulas + auto-sized cols
    // ════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] riskRegisterXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        ExportAuditContext.record(risks.size(), 1, "ExportService.riskRegister");   // F2-41

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Risk Register");
            CellStyle hdrStyle = headerStyle(wb);
            CellStyle wrapStyle = wrapStyle(wb);

            String[] headers = { "Code", "Title (EN)", "Title (AR)", "Status",
                "Inherent L", "Inherent I", "Inherent Score",
                "Residual L", "Residual I", "Residual Score (formula)" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }

            int rowIdx = 1;
            for (Risk r : risks) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("R-" + String.format(Locale.ROOT, "%05d", r.getId()));
                Cell t1 = row.createCell(1); t1.setCellValue(safe(r.getTitleEn())); t1.setCellStyle(wrapStyle);
                Cell t2 = row.createCell(2); t2.setCellValue(safe(r.getTitleAr())); t2.setCellStyle(wrapStyle);
                row.createCell(3).setCellValue(safe(r.getStatus()));
                if (r.getInherentLikelihood() != null) row.createCell(4).setCellValue(r.getInherentLikelihood());
                if (r.getInherentImpact()     != null) row.createCell(5).setCellValue(r.getInherentImpact());
                if (r.getInherentScore()      != null) row.createCell(6).setCellValue(r.getInherentScore());
                if (r.getResidualLikelihood() != null) row.createCell(7).setCellValue(r.getResidualLikelihood());
                if (r.getResidualImpact()     != null) row.createCell(8).setCellValue(r.getResidualImpact());
                // Residual score as a formula so users can edit L/I and have score recompute
                Cell residualScoreCell = row.createCell(9);
                residualScoreCell.setCellFormula("IF(AND(ISNUMBER(H" + (rowIdx) + "),ISNUMBER(I" + (rowIdx) + ")),H" + (rowIdx) + "*I" + (rowIdx) + ",\"\")");
            }

            // Total + count formulas at bottom (like a spreadsheet user would)
            int totalRow = rowIdx + 1;
            Row tr = sheet.createRow(totalRow);
            Cell label = tr.createCell(5); label.setCellValue("Total / Avg →");
            label.setCellStyle(hdrStyle);
            Cell sumCell = tr.createCell(6);
            sumCell.setCellFormula("SUM(G2:G" + rowIdx + ")");
            Cell avgCell = tr.createCell(9);
            avgCell.setCellFormula("IFERROR(AVERAGE(J2:J" + rowIdx + "),\"\")");

            // P1: frozen header + auto-filter (was freeze-only), plus traffic-light conditional
            // formatting auto-applied to any status/severity/band column. rowIdx-1 = last data
            // row (excludes any totals row written below the data).
            XlsxStyler.freezeAndFilter(sheet, headers.length, rowIdx - 1);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(sheet, i, 1, rowIdx - 1);
            }
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            // Only I/O faults from workbook construction/serialisation are wrapped;
            // any RuntimeException propagates to the GlobalExceptionHandler unaltered.
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] findingsXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<AuditFinding> findings = findingRepo.findByOrganisationId(org, PageRequest.of(0, 1000)).getContent();
        ExportAuditContext.record(findings.size(), 1, "ExportService.findingsAging");   // F2-41
        LocalDate today = LocalDate.now();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Findings");
            CellStyle hdrStyle = headerStyle(wb);
            CellStyle wrapStyle = wrapStyle(wb);
            String[] headers = { "Title", "Severity", "Owner", "Created", "Days Open",
                "Tier", "CAPA Stage", "Status", "Days Open (formula)" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }
            int rowIdx = 1;
            for (AuditFinding f : findings) {
                Row row = sheet.createRow(rowIdx++);
                Cell t = row.createCell(0); t.setCellValue(safe(f.getTitle())); t.setCellStyle(wrapStyle);
                row.createCell(1).setCellValue(safe(f.getSeverity()));
                row.createCell(2).setCellValue(safe(f.getOwner()));
                LocalDateTime created = f.getCreatedAt();
                if (created != null) row.createCell(3).setCellValue(created.toLocalDate().toString());
                long daysOpen = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), today) : 0;
                row.createCell(4).setCellValue(daysOpen);
                String tier = daysOpen >= 90 ? "EXEC" : daysOpen >= 60 ? "DIRECTOR" : daysOpen >= 30 ? "MANAGER" : "ON_TRACK";
                row.createCell(5).setCellValue(tier);
                row.createCell(6).setCellValue(safe(f.getCapaStage()));
                row.createCell(7).setCellValue(safe(f.getStatus()));
                // Live "Days Open" formula recomputed when the workbook is opened
                Cell daysFormula = row.createCell(8);
                daysFormula.setCellFormula("IF(D" + rowIdx + "<>\"\",TODAY()-DATEVALUE(D" + rowIdx + "),\"\")");
            }
            // P1: frozen header + auto-filter (was freeze-only), plus traffic-light conditional
            // formatting auto-applied to any status/severity/band column. rowIdx-1 = last data
            // row (excludes any totals row written below the data).
            XlsxStyler.freezeAndFilter(sheet, headers.length, rowIdx - 1);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(sheet, i, 1, rowIdx - 1);
            }
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            // Only I/O faults from workbook construction/serialisation are wrapped;
            // any RuntimeException propagates to the GlobalExceptionHandler unaltered.
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] kriDashboardXlsx() {
        Long org = TenantContext.getCurrentTenant();
        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(org);
        ExportAuditContext.record(kris.size(), 1, "ExportService.kriDashboard");   // F2-41
        // QUAL-BCMP-006: one query for each KRI's latest reading instead of a
        // per-KRI N+1 loop. Key present ⇒ a reading exists; value [0]=band (may
        // be null), [1]=readingValue.
        Map<Long, Object[]> latestByKri = latestReadingByKri(kris);

        try (Workbook wb = new XSSFWorkbook()) {
            // ── Sheet 1: KRIs ─────────────────────────────────
            Sheet sheet = wb.createSheet("KRIs");
            CellStyle hdrStyle = headerStyle(wb);
            String[] headers = { "Code", "KRI", "Direction", "Green ≤", "Amber ≤",
                "Latest Reading", "Latest Band", "Frequency", "Owner", "Framework" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hdrStyle);
            }
            int rowIdx = 1;
            for (KriDefinition k : kris) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(k.getKriCode()));
                row.createCell(1).setCellValue(safe(k.getTitleEn()));
                row.createCell(2).setCellValue(safe(k.getValueDirection()));
                if (k.getGreenThreshold() != null) row.createCell(3).setCellValue(k.getGreenThreshold().doubleValue());
                if (k.getAmberThreshold() != null) row.createCell(4).setCellValue(k.getAmberThreshold().doubleValue());
                Object[] latest = latestByKri.get(k.getId());
                if (latest != null) {
                    row.createCell(5).setCellValue(((BigDecimal) latest[1]).doubleValue());
                    row.createCell(6).setCellValue(safe((String) latest[0]));
                }
                row.createCell(7).setCellValue(safe(k.getFrequency()));
                row.createCell(8).setCellValue(k.getOwnerUserId() != null ? String.valueOf(k.getOwnerUserId()) : "—");
                row.createCell(9).setCellValue(safe(k.getClauseRef()));
            }
            // ── Sheet 2: Summary with formulas ───────────────
            Sheet summary = wb.createSheet("Summary");
            summary.createRow(0).createCell(0).setCellValue("Band");
            summary.getRow(0).createCell(1).setCellValue("Count");
            summary.getRow(0).getCell(0).setCellStyle(hdrStyle);
            summary.getRow(0).getCell(1).setCellStyle(hdrStyle);
            String[] bands = { "GREEN", "AMBER", "RED" };
            for (int i = 0; i < bands.length; i++) {
                Row r = summary.createRow(i + 1);
                r.createCell(0).setCellValue(bands[i]);
                Cell c = r.createCell(1);
                c.setCellFormula("COUNTIF(KRIs!G2:G" + rowIdx + ",\"" + bands[i] + "\")");
            }
            Row totalRow = summary.createRow(bands.length + 2);
            totalRow.createCell(0).setCellValue("Total");
            totalRow.getCell(0).setCellStyle(hdrStyle);
            Cell totalCell = totalRow.createCell(1);
            totalCell.setCellFormula("SUM(B2:B" + (bands.length + 1) + ")");
            totalCell.setCellStyle(hdrStyle);

            // P1: frozen header + auto-filter (was freeze-only), plus traffic-light conditional
            // formatting auto-applied to any status/severity/band column. rowIdx-1 = last data
            // row (excludes any totals row written below the data).
            XlsxStyler.freezeAndFilter(sheet, headers.length, rowIdx - 1);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (XlsxStyler.isStatusHeader(headers[i])) XlsxStyler.statusColors(sheet, i, 1, rowIdx - 1);
            }
            summary.autoSizeColumn(0); summary.autoSizeColumn(1);
            return writeWorkbook(wb);
        } catch (java.io.IOException e) {
            // Only I/O faults from workbook construction/serialisation are wrapped;
            // any RuntimeException propagates to the GlobalExceptionHandler unaltered.
            throw new ExportException("Excel", "Excel generation failed", e);
        }
    }

    /** Brand-aware header style — fills with the tenant's primary colour (P2 BrandTheme),
     *  falling back to the shared house teal when no brand is set. */
    private CellStyle headerStyle(Workbook wb) {
        return XlsxStyler.headerStyle(wb, brandThemeService.resolve().primaryColor());
    }
    private CellStyle wrapStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }
    private byte[] writeWorkbook(Workbook wb) throws java.io.IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
