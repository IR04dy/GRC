package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.ControlAssessment;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.entity.CyberIncident;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.ControlAssessmentRepository;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.reporting.service.PdfTheme;
import com.tss.grc.module.reporting.service.RegisterExport;
import com.tss.grc.module.reporting.service.RegisterExport.Col;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * CY-F — regulator/board-ready exports of the cyber registers. Each register
 * renders to an Excel workbook (POI) or an Arabic-capable executive PDF
 * (OpenPDF), reusing the shared {@link RegisterExport} engine + {@link PdfTheme}
 * so the output matches the platform's board pack / BCP / ESG house style.
 *
 * <p>{@code register} and {@code format} are validated through
 * {@link CyberInputValidation} (CY-H) so a bad value returns 400, not 500.
 */
@Service
@RequiredArgsConstructor
public class CyberExportService {

    /** Exportable registers — the value accepted on {@code ?register=}. */
    public static final Set<String> REGISTERS = Set.of(
            "CONTROLS", "VULNERABILITIES", "ASSESSMENTS", "INCIDENTS", "ACTION-PLANS", "EVIDENCE");
    private static final Set<String> FORMATS = Set.of("XLSX", "PDF");
    private static final Pageable CAP = PageRequest.of(0, 5000);
    private static final DateTimeFormatter DFMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CyberControlRepository controlRepo;
    private final VulnerabilityRepository vulnRepo;
    private final ControlAssessmentRepository assessmentRepo;
    private final CyberIncidentRepository incidentRepo;
    private final CyberActionPlanRepository actionPlanRepo;
    private final CyberEvidenceRepository evidenceRepo;
    private final OrganisationRepository orgRepo;

    @Transactional(readOnly = true)
    public byte[] export(String register, String format, String lang) {
        String reg = CyberInputValidation.checkEnum(register, "register", REGISTERS, true, null);
        String fmt = CyberInputValidation.checkEnum(format, "format", FORMATS, false, "XLSX");
        Long orgId = TenantContext.getCurrentTenant();
        Organisation org = orgId == null ? null : orgRepo.findById(orgId).orElse(null);
        boolean ar = "ar".equalsIgnoreCase(lang);
        return switch (reg) {
            case "CONTROLS"        -> controls(orgId, org, ar, fmt);
            case "VULNERABILITIES" -> vulnerabilities(orgId, org, ar, fmt);
            case "ASSESSMENTS"     -> assessments(orgId, org, ar, fmt);
            case "INCIDENTS"       -> incidents(orgId, org, ar, fmt);
            case "ACTION-PLANS"    -> actionPlans(orgId, org, ar, fmt);
            case "EVIDENCE"        -> evidence(orgId, org, ar, fmt);
            default -> throw new IllegalArgumentException("Unknown register: " + register);
        };
    }

    // ── Registers ──────────────────────────────────────────────────────────

    private byte[] controls(Long orgId, Organisation org, boolean ar, String fmt) {
        List<CyberControl> rows = controlRepo.findByOrganisationId(orgId, CAP).getContent();
        List<Col<CyberControl>> cols = List.of(
                Col.of("Control #", CyberControl::getControlNumber),
                Col.of("Framework", CyberControl::getFrameworkName),
                Col.of("Domain", CyberControl::getDomain),
                Col.of(ar ? "العنوان" : "Title", c -> title(c.getTitleEn(), c.getTitleAr(), ar)),
                Col.of("Type", CyberControl::getControlType),
                Col.of("Owner", CyberControl::getControlOwner),
                Col.of("Maturity", CyberControl::getMaturityLevel),
                Col.of("Implementation", CyberControl::getImplementationStatus),
                Col.of("Status", CyberControl::getStatus));
        List<PdfTheme.Kpi> kpis = List.of(new PdfTheme.Kpi("Total controls", String.valueOf(rows.size()), ""));
        return emit(fmt, org, "Cyber Controls", "الضوابط السيبرانية",
                "Cyber Control Register", "سجل الضوابط السيبرانية", kpis, cols, rows);
    }

    private byte[] vulnerabilities(Long orgId, Organisation org, boolean ar, String fmt) {
        List<Vulnerability> rows = vulnRepo.findByOrganisationId(orgId, CAP).getContent();
        List<Col<Vulnerability>> cols = List.of(
                Col.of("CVE", Vulnerability::getCveId),
                Col.of(ar ? "العنوان" : "Title", v -> title(v.getTitleEn(), v.getTitleAr(), ar)),
                Col.of("Asset", Vulnerability::getAssetName),
                Col.of("Severity", Vulnerability::getSeverity),
                Col.of("CVSS", v -> v.getCvssScore() == null ? "" : v.getCvssScore().toPlainString()),
                Col.of("Type", Vulnerability::getVulnerabilityType),
                Col.of("Owner", Vulnerability::getOwnerName),
                Col.of("SLA days", v -> num(v.getSlaDays())),
                Col.of("Due", v -> d(v.getDueDate())),
                Col.of("Status", Vulnerability::getWorkflowStatus),
                Col.of("Overdue", v -> Boolean.TRUE.equals(v.getOverdue()) ? "Yes" : "No"));
        long crit = rows.stream().filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity())).count();
        long high = rows.stream().filter(v -> "HIGH".equalsIgnoreCase(v.getSeverity())).count();
        long over = rows.stream().filter(v -> Boolean.TRUE.equals(v.getOverdue())).count();
        List<PdfTheme.Kpi> kpis = List.of(
                new PdfTheme.Kpi("Total", String.valueOf(rows.size()), ""),
                new PdfTheme.Kpi("Critical", String.valueOf(crit), ""),
                new PdfTheme.Kpi("High", String.valueOf(high), ""),
                new PdfTheme.Kpi("Overdue", String.valueOf(over), "past SLA"));
        return emit(fmt, org, "Vulnerabilities", "الثغرات",
                "Vulnerability Register", "سجل الثغرات", kpis, cols, rows);
    }

    private byte[] assessments(Long orgId, Organisation org, boolean ar, String fmt) {
        List<ControlAssessment> rows = assessmentRepo.findByOrganisationId(orgId, CAP).getContent();
        List<Col<ControlAssessment>> cols = List.of(
                Col.of("ID", a -> num(a.getId())),
                Col.of("Control ID", a -> num(a.getControlId())),
                Col.of("Assessor", ControlAssessment::getAssessorName),
                Col.of("Reviewer", ControlAssessment::getReviewerName),
                Col.of("Result", ControlAssessment::getResult),
                Col.of("Maturity", a -> num(a.getMaturityScore())),
                Col.of("Risk", ControlAssessment::getRiskLevel),
                Col.of("Gap?", a -> Boolean.TRUE.equals(a.getGapIdentified()) ? "Yes" : "No"),
                Col.of("Evidence #", a -> String.valueOf(a.getEvidenceCount())),
                Col.of("Status", ControlAssessment::getStatus));
        long gaps = rows.stream().filter(a -> Boolean.TRUE.equals(a.getGapIdentified())).count();
        long appr = rows.stream().filter(a -> "APPROVED".equalsIgnoreCase(a.getStatus())).count();
        List<PdfTheme.Kpi> kpis = List.of(
                new PdfTheme.Kpi("Total", String.valueOf(rows.size()), ""),
                new PdfTheme.Kpi("Gaps", String.valueOf(gaps), "identified"),
                new PdfTheme.Kpi("Approved", String.valueOf(appr), ""));
        return emit(fmt, org, "Control Assessments", "تقييم الضوابط",
                "Control Assessment Register", "سجل تقييم الضوابط", kpis, cols, rows);
    }

    private byte[] incidents(Long orgId, Organisation org, boolean ar, String fmt) {
        List<CyberIncident> rows = incidentRepo.findByOrganisationId(orgId, CAP).getContent();
        List<Col<CyberIncident>> cols = List.of(
                Col.of("Incident #", CyberIncident::getIncidentNumber),
                Col.of(ar ? "العنوان" : "Title", i -> title(i.getTitleEn(), i.getTitleAr(), ar)),
                Col.of("Type", CyberIncident::getIncidentType),
                Col.of("Severity", CyberIncident::getSeverity),
                Col.of("Impact", CyberIncident::getImpact),
                Col.of("Lead", CyberIncident::getIncidentLead),
                Col.of("Detected", i -> dt(i.getDetectedAt())),
                Col.of("Resolved", i -> dt(i.getResolvedAt())),
                Col.of("Status", CyberIncident::getWorkflowStatus));
        long open = rows.stream().filter(i -> !"CLOSED".equalsIgnoreCase(i.getWorkflowStatus())
                && !"RESOLVED".equalsIgnoreCase(i.getWorkflowStatus())).count();
        List<PdfTheme.Kpi> kpis = List.of(
                new PdfTheme.Kpi("Total", String.valueOf(rows.size()), ""),
                new PdfTheme.Kpi("Open", String.valueOf(open), "not closed"));
        return emit(fmt, org, "Cyber Incidents", "الحوادث السيبرانية",
                "Cyber Incident Register", "سجل الحوادث السيبرانية", kpis, cols, rows);
    }

    private byte[] actionPlans(Long orgId, Organisation org, boolean ar, String fmt) {
        List<CyberActionPlan> rows = actionPlanRepo.findByOrganisationId(orgId, CAP).getContent();
        LocalDate today = LocalDate.now();
        List<Col<CyberActionPlan>> cols = List.of(
                Col.of("ID", a -> num(a.getId())),
                Col.of(ar ? "العنوان" : "Title", a -> title(a.getTitleEn(), a.getTitleAr(), ar)),
                Col.of("Source", CyberActionPlan::getSourceType),
                Col.of("Priority", CyberActionPlan::getPriority),
                Col.of("Owner", CyberActionPlan::getOwnerName),
                Col.of("Due", a -> d(a.getDueDate())),
                Col.of("Progress %", a -> String.valueOf(a.getProgressPct())),
                Col.of("Status", CyberActionPlan::getWorkflowStatus));
        long completed = rows.stream().filter(a -> "COMPLETED".equalsIgnoreCase(a.getWorkflowStatus())).count();
        long overdue = rows.stream().filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today)
                && !"COMPLETED".equalsIgnoreCase(a.getWorkflowStatus())).count();
        List<PdfTheme.Kpi> kpis = List.of(
                new PdfTheme.Kpi("Total", String.valueOf(rows.size()), ""),
                new PdfTheme.Kpi("Overdue", String.valueOf(overdue), "past due"),
                new PdfTheme.Kpi("Completed", String.valueOf(completed), ""));
        return emit(fmt, org, "CAPA / Action Plans", "خطط العمل التصحيحية",
                "Cyber Action Plan Register", "سجل خطط العمل السيبرانية", kpis, cols, rows);
    }

    private byte[] evidence(Long orgId, Organisation org, boolean ar, String fmt) {
        List<CyberEvidence> rows = evidenceRepo.findByOrganisationId(orgId, CAP).getContent();
        List<Col<CyberEvidence>> cols = List.of(
                Col.of("ID", e -> num(e.getId())),
                Col.of(ar ? "العنوان" : "Title", CyberEvidence::getTitleEn),
                Col.of("Type", CyberEvidence::getEvidenceType),
                Col.of("Control ID", e -> num(e.getControlId())),
                Col.of("Owner", CyberEvidence::getOwnerName),
                Col.of("Status", CyberEvidence::getStatus),
                Col.of("Review", e -> d(e.getReviewDate())),
                Col.of("Expiry", e -> d(e.getExpiryDate())));
        long expired = rows.stream().filter(e -> "EXPIRED".equalsIgnoreCase(e.getStatus())).count();
        long current = rows.stream().filter(e -> "CURRENT".equalsIgnoreCase(e.getStatus())).count();
        List<PdfTheme.Kpi> kpis = List.of(
                new PdfTheme.Kpi("Total", String.valueOf(rows.size()), ""),
                new PdfTheme.Kpi("Current", String.valueOf(current), ""),
                new PdfTheme.Kpi("Expired", String.valueOf(expired), ""));
        return emit(fmt, org, "Cyber Evidence", "الأدلة السيبرانية",
                "Cyber Evidence Register", "سجل الأدلة السيبرانية", kpis, cols, rows);
    }

    // ── Dispatch + helpers ───────────────────────────────────────────────

    private <T> byte[] emit(String fmt, Organisation org, String eyebrowEn, String eyebrowAr,
                            String titleEn, String titleAr, List<PdfTheme.Kpi> kpis,
                            List<Col<T>> cols, List<T> rows) {
        String orgEn = org == null ? null : org.getNameEn();
        String orgAr = org == null ? null : org.getNameAr();
        return "PDF".equalsIgnoreCase(fmt)
                ? RegisterExport.pdf(orgEn, orgAr, eyebrowEn, eyebrowAr, titleEn, titleAr, kpis, cols, rows)
                : RegisterExport.xlsx(titleEn, cols, rows);
    }

    private static String title(String en, String arText, boolean ar) {
        if (ar && arText != null && !arText.isBlank()) return arText;
        return en == null ? "" : en;
    }
    private static String num(Number n) { return n == null ? "" : n.toString(); }
    private static String d(LocalDate v) { return v == null ? "" : v.format(DFMT); }
    private static String dt(LocalDateTime v) { return v == null ? "" : v.toLocalDate().format(DFMT); }
}
