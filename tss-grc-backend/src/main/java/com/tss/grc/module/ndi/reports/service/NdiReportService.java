package com.tss.grc.module.ndi.reports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService.CycleRollup;
import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import com.tss.grc.module.ndi.evidence.repository.NdiEvidenceItemRepository;
import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.entity.NdiGap;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.gap.repository.NdiGapRepository;
import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import com.tss.grc.module.ndi.maturity.repository.NdiMaturityAssessmentRepository;
import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import com.tss.grc.module.ndi.opex.repository.NdiOperationalExcellenceKpiRepository;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanRepository;
import com.tss.grc.module.ndi.reports.entity.NdiReportDefinition;
import com.tss.grc.module.ndi.reports.repository.NdiReportDefinitionRepository;
import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import com.tss.grc.module.ndi.submission.repository.NdiSubmissionPackageRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NDI Reports service — slice 11.
 *
 * <p>Owns the CRUD of saved report definitions and the synthesizer
 * that turns a report definition into structured rows / aggregates the
 * UI can render or export. Heavy-format export (PDF / XLSX) is wired
 * up downstream; for now every report returns JSON-shaped data and
 * the controller exposes a generic /run endpoint.
 */
@Service
@Transactional
public class NdiReportService {

    private final NdiReportDefinitionRepository defs;
    private final NdiAssessmentCycleRepository cycles;
    private final NdiMaturityAssessmentRepository maturity;
    private final NdiComplianceAssessmentRepository compliance;
    private final NdiOperationalExcellenceKpiRepository opex;
    private final NdiEvidenceItemRepository evidence;
    private final NdiGapRepository gaps;
    private final NdiCorrectiveActionRepository capas;
    private final NdiImprovementPlanRepository plans;
    private final NdiSubmissionPackageRepository submissions;
    private final NdiGovernanceDomainService domainService;
    @SuppressWarnings("unused") private final ObjectMapper json;

    public NdiReportService(NdiReportDefinitionRepository defs,
                            NdiAssessmentCycleRepository cycles,
                            NdiMaturityAssessmentRepository maturity,
                            NdiComplianceAssessmentRepository compliance,
                            NdiOperationalExcellenceKpiRepository opex,
                            NdiEvidenceItemRepository evidence,
                            NdiGapRepository gaps,
                            NdiCorrectiveActionRepository capas,
                            NdiImprovementPlanRepository plans,
                            NdiSubmissionPackageRepository submissions,
                            NdiGovernanceDomainService domainService,
                            ObjectMapper json) {
        this.defs = defs;
        this.cycles = cycles;
        this.maturity = maturity;
        this.compliance = compliance;
        this.opex = opex;
        this.evidence = evidence;
        this.gaps = gaps;
        this.capas = capas;
        this.plans = plans;
        this.submissions = submissions;
        this.domainService = domainService;
        this.json = json;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    public record ReportResult(
        String reportType,
        Long cycleId,
        LocalDateTime generatedAt,
        Map<String, Object> summary,
        List<Map<String, Object>> rows
    ) {}

    @Transactional(readOnly = true)
    public List<NdiReportDefinition> list(String typeFilter) {
        Long o = org();
        return typeFilter == null
            ? defs.findByOrganisationIdOrderByReportCodeAsc(o)
            : defs.findByOrganisationIdAndReportTypeOrderByReportCodeAsc(o, typeFilter);
    }

    @Transactional(readOnly = true)
    public NdiReportDefinition get(Long id) {
        return defs.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Report not found: " + id));
    }

    public NdiReportDefinition create(NdiReportDefinition body) {
        if (body.getNameEn() == null || body.getNameEn().isBlank())
            throw new IllegalArgumentException("nameEn is required");
        if (body.getReportType() == null) throw new IllegalArgumentException("reportType is required");
        body.setId(null);
        body.setOrganisationId(org());
        return defs.save(body);
    }

    public NdiReportDefinition update(Long id, NdiReportDefinition body) {
        NdiReportDefinition d = get(id);
        if (body.getReportCode()    != null) d.setReportCode(body.getReportCode());
        if (body.getNameEn()        != null) d.setNameEn(body.getNameEn());
        if (body.getNameAr()        != null) d.setNameAr(body.getNameAr());
        if (body.getDescriptionEn() != null) d.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr() != null) d.setDescriptionAr(body.getDescriptionAr());
        if (body.getReportType()    != null) d.setReportType(body.getReportType());
        if (body.getFormat()        != null) d.setFormat(body.getFormat());
        if (body.getLanguage()      != null) d.setLanguage(body.getLanguage());
        if (body.getFiltersJson()   != null) d.setFiltersJson(body.getFiltersJson());
        if (body.getScheduleCron()  != null) d.setScheduleCron(body.getScheduleCron());
        if (body.getRecipients()    != null) d.setRecipients(body.getRecipients());
        if (body.getStatus()        != null) d.setStatus(body.getStatus());
        return defs.save(d);
    }

    public void delete(Long id) {
        NdiReportDefinition d = get(id);
        d.setIsDeleted(true);
        defs.save(d);
    }

    /** Run a saved report and stamp last_run_* fields. */
    public ReportResult run(Long id, Long cycleId) {
        NdiReportDefinition d = get(id);
        try {
            ReportResult r = runAdHoc(d.getReportType(), cycleId);
            d.setLastRunAt(LocalDateTime.now());
            d.setLastRunStatus("SUCCESS");
            d.setLastRunMessage("Rows: " + r.rows.size());
            d.setLastRunSizeBytes((long) r.rows.size());
            defs.save(d);
            return r;
        } catch (Exception ex) {
            d.setLastRunAt(LocalDateTime.now());
            d.setLastRunStatus("FAILED");
            d.setLastRunMessage(ex.getMessage());
            defs.save(d);
            throw ex;
        }
    }

    /** Run an ad-hoc report by type without persisting metadata. */
    @Transactional(readOnly = true)
    public ReportResult runAdHoc(String reportType, Long cycleId) {
        return switch (reportType) {
            case "NDI_EXECUTIVE_SUMMARY"     -> executiveSummary(cycleId);
            case "MATURITY_HEATMAP"          -> maturityHeatmap(cycleId);
            case "COMPLIANCE_STATUS"         -> complianceStatus(cycleId);
            case "OPEX_KPI_DASHBOARD"        -> opexDashboard(cycleId);
            case "GAP_REGISTER"              -> gapRegister(cycleId);
            case "CAPA_REGISTER"             -> capaRegister(cycleId);
            case "IMPROVEMENT_PLAN_ROADMAP"  -> roadmap(cycleId);
            case "EVIDENCE_INVENTORY"        -> evidenceInventory(cycleId);
            case "DOMAIN_ROLLUP"             -> domainRollup(cycleId);
            case "CYCLE_HISTORY"             -> cycleHistory();
            case "AUDIT_TRAIL"               -> auditTrail(cycleId);
            case "SUBMISSION_PACKAGE_EXPORT" -> submissionExport(cycleId);
            default -> throw new IllegalArgumentException("Unknown report type: " + reportType);
        };
    }

    // ─── Synthesizers ──────────────────────────────────────────────

    private ReportResult executiveSummary(Long cycleId) {
        Long o = org();
        CycleRollup rollup = domainService.rollupForCycle(cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("overallNdiPct", rollup.overallNdiPct());
        sum.put("domainCount", rollup.domains().size());
        sum.put("totalEvidence", evidence.findByOrganisationIdAndCycleId(o, cycleId).size());
        sum.put("totalGaps", gaps.findByOrganisationIdAndCycleId(o, cycleId).size());
        long critical = gaps.findByOrganisationIdAndCycleId(o, cycleId).stream()
            .filter(g -> "CRITICAL".equals(g.getSeverity())).count();
        sum.put("criticalGaps", critical);
        List<Map<String, Object>> rows = rollup.domains().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.code());
            m.put("nameEn", d.nameEn());
            m.put("nameAr", d.nameAr());
            m.put("weight", d.weight());
            m.put("blendedScorePct", d.blendedScorePct());
            return m;
        }).toList();
        return new ReportResult("NDI_EXECUTIVE_SUMMARY", cycleId, LocalDateTime.now(), sum, rows);
    }

    private ReportResult maturityHeatmap(Long cycleId) {
        Long o = org();
        List<NdiMaturityAssessment> rows = maturity.findByOrganisationIdAndCycleId(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        Map<String, Long> byDomain = rows.stream()
            .collect(Collectors.groupingBy(NdiMaturityAssessment::getDomain, Collectors.counting()));
        sum.put("byDomain", byDomain);
        List<Map<String, Object>> out = rows.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("domain", m.getDomain());
            row.put("currentLevel", m.getCurrentLevel());
            row.put("targetLevel", m.getTargetLevel());
            row.put("status", m.getStatus());
            return row;
        }).toList();
        return new ReportResult("MATURITY_HEATMAP", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult complianceStatus(Long cycleId) {
        Long o = org();
        List<NdiComplianceAssessment> rows = compliance.findByOrganisationIdAndCycleId(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        Map<String, Long> byStatus = rows.stream()
            .collect(Collectors.groupingBy(NdiComplianceAssessment::getComplianceStatus, Collectors.counting()));
        sum.put("byStatus", byStatus);
        List<Map<String, Object>> out = rows.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("requirementCode", c.getRequirementCode());
            row.put("domain", c.getDomain());
            row.put("complianceStatus", c.getComplianceStatus());
            row.put("lifecycleStatus", c.getLifecycleStatus());
            row.put("complianceScore", c.getComplianceScore());
            row.put("needsGap", c.getNeedsGap());
            return row;
        }).toList();
        return new ReportResult("COMPLIANCE_STATUS", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult opexDashboard(Long cycleId) {
        Long o = org();
        List<NdiOperationalExcellenceKpi> rows = opex.findByOrganisationIdAndCycleId(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        Map<String, Long> byStatus = rows.stream()
            .collect(Collectors.groupingBy(NdiOperationalExcellenceKpi::getStatus, Collectors.counting()));
        sum.put("byStatus", byStatus);
        List<Map<String, Object>> out = rows.stream().map(k -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kpiCode", k.getKpiCode());
            row.put("indicatorNameEn", k.getIndicatorNameEn());
            row.put("indicatorNameAr", k.getIndicatorNameAr());
            row.put("category", k.getCategory());
            row.put("baseline", k.getBaseline());
            row.put("target", k.getTarget());
            row.put("actual", k.getActual());
            row.put("status", k.getStatus());
            return row;
        }).toList();
        return new ReportResult("OPEX_KPI_DASHBOARD", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult gapRegister(Long cycleId) {
        Long o = org();
        List<NdiGap> rows = gaps.findByOrganisationIdAndCycleId(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        sum.put("bySeverity", rows.stream().collect(Collectors.groupingBy(NdiGap::getSeverity, Collectors.counting())));
        List<Map<String, Object>> out = rows.stream().map(g -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gapCode", g.getGapCode());
            row.put("titleEn", g.getTitleEn());
            row.put("titleAr", g.getTitleAr());
            row.put("severity", g.getSeverity());
            row.put("status", g.getStatus());
            row.put("ownerName", g.getOwnerName());
            row.put("targetDate", g.getTargetDate());
            row.put("capaCount", g.getCapaCount());
            row.put("rollupProgress", g.getRollupProgress());
            return row;
        }).toList();
        return new ReportResult("GAP_REGISTER", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult capaRegister(Long cycleId) {
        Long o = org();
        Set<Long> gapIds = gaps.findByOrganisationIdAndCycleId(o, cycleId).stream()
            .map(NdiGap::getId).collect(Collectors.toSet());
        List<NdiCorrectiveAction> rows = capas.findByOrganisationId(o).stream()
            .filter(c -> gapIds.contains(c.getGapId())).toList();
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        sum.put("byStatus", rows.stream().collect(Collectors.groupingBy(NdiCorrectiveAction::getStatus, Collectors.counting())));
        List<Map<String, Object>> out = rows.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("actionCode", c.getActionCode());
            row.put("gapId", c.getGapId());
            row.put("actionEn", c.getActionEn());
            row.put("actionAr", c.getActionAr());
            row.put("actionType", c.getActionType());
            row.put("ownerName", c.getOwnerName());
            row.put("targetDate", c.getTargetDate());
            row.put("progressPct", c.getProgressPct());
            row.put("status", c.getStatus());
            return row;
        }).toList();
        return new ReportResult("CAPA_REGISTER", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult roadmap(Long cycleId) {
        Long o = org();
        List<NdiImprovementPlan> rows = plans.findByOrganisationIdAndCycleIdOrderByPeriodStartDesc(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("planCount", rows.size());
        List<Map<String, Object>> out = rows.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("planCode", p.getPlanCode());
            row.put("nameEn", p.getNameEn());
            row.put("nameAr", p.getNameAr());
            row.put("status", p.getStatus());
            row.put("periodStart", p.getPeriodStart());
            row.put("periodEnd", p.getPeriodEnd());
            row.put("progressPct", p.getProgressPct());
            row.put("totalItems", p.getTotalItems());
            row.put("completedItems", p.getCompletedItems());
            row.put("budgetTotal", p.getBudgetTotal());
            return row;
        }).toList();
        return new ReportResult("IMPROVEMENT_PLAN_ROADMAP", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult evidenceInventory(Long cycleId) {
        Long o = org();
        List<NdiEvidenceItem> rows = evidence.findByOrganisationIdAndCycleId(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        sum.put("byType", rows.stream().collect(Collectors.groupingBy(NdiEvidenceItem::getEvidenceType, Collectors.counting())));
        sum.put("byStatus", rows.stream().collect(Collectors.groupingBy(NdiEvidenceItem::getStatus, Collectors.counting())));
        List<Map<String, Object>> out = rows.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("evidenceCode", e.getEvidenceCode());
            row.put("titleEn", e.getTitleEn());
            row.put("titleAr", e.getTitleAr());
            row.put("evidenceType", e.getEvidenceType());
            row.put("classification", e.getClassification());
            row.put("status", e.getStatus());
            row.put("expiryDate", e.getExpiryDate());
            return row;
        }).toList();
        return new ReportResult("EVIDENCE_INVENTORY", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult domainRollup(Long cycleId) {
        CycleRollup rollup = domainService.rollupForCycle(cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("overallNdiPct", rollup.overallNdiPct());
        sum.put("totalWeight", rollup.totalWeight());
        List<Map<String, Object>> out = rollup.domains().stream().map(d -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", d.code());
            row.put("nameEn", d.nameEn());
            row.put("nameAr", d.nameAr());
            row.put("weight", d.weight());
            row.put("isPlaceholder", d.isPlaceholder());
            row.put("maturityCount", d.maturityCount());
            row.put("maturityScorePct", d.maturityScorePct());
            row.put("complianceCount", d.complianceCount());
            row.put("compliancePct", d.compliancePct());
            row.put("opexCount", d.opexCount());
            row.put("opexScorePct", d.opexScorePct());
            row.put("blendedScorePct", d.blendedScorePct());
            return row;
        }).toList();
        return new ReportResult("DOMAIN_ROLLUP", cycleId, LocalDateTime.now(), sum, out);
    }

    private ReportResult cycleHistory() {
        // Tenant scoped at the DB layer (replaces an earlier
        // findAll().stream().filter pattern that pulled every org's
        // cycles into memory). The @Where(is_deleted=0) on the entity
        // already filters out soft-deleted rows.
        var all = cycles.findByOrganisationIdOrderByReportingYearDesc(org());
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", all.size());
        List<Map<String, Object>> out = all.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("cycleCode", c.getCycleCode());
            row.put("nameEn", c.getCycleNameEn());
            row.put("nameAr", c.getCycleNameAr());
            row.put("reportingYear", c.getReportingYear());
            row.put("status", c.getStatus());
            return row;
        }).toList();
        return new ReportResult("CYCLE_HISTORY", null, LocalDateTime.now(), sum, out);
    }

    /** Audit trail synthesizer — kept minimal here; full audit query lives in slice 14. */
    private ReportResult auditTrail(Long cycleId) {
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("note", "Use /api/ndi/audit?cycleId=... for the full audit ledger; this stub holds the metadata.");
        return new ReportResult("AUDIT_TRAIL", cycleId, LocalDateTime.now(), sum, List.of());
    }

    private ReportResult submissionExport(Long cycleId) {
        Long o = org();
        List<NdiSubmissionPackage> rows = submissions.findByOrganisationIdAndCycleIdOrderByVersionDesc(o, cycleId);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("count", rows.size());
        List<Map<String, Object>> out = rows.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("packageCode", p.getPackageCode());
            row.put("version", p.getVersion());
            row.put("status", p.getStatus());
            row.put("overallNdiPct", p.getOverallNdiPct());
            row.put("totalEvidence", p.getTotalEvidence());
            row.put("totalGaps", p.getTotalGaps());
            row.put("criticalGaps", p.getCriticalGaps());
            row.put("totalActions", p.getTotalActions());
            row.put("completedActions", p.getCompletedActions());
            row.put("manifestChecksum", p.getManifestChecksum());
            row.put("submittedAt", p.getSubmittedAt());
            row.put("sdaiaReference", p.getSdaiaReference());
            return row;
        }).toList();
        return new ReportResult("SUBMISSION_PACKAGE_EXPORT", cycleId, LocalDateTime.now(), sum, out);
    }
}
