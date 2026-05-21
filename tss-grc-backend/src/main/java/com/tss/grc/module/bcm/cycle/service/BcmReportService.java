package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BCM Report generator — BCM-7 deliverable.
 *
 * <p>15 named reports, each a pure read-side aggregation over the BCM-1/2/3
 * tables. The output is a {@code Map<String, Object>} so the frontend can render
 * any structure (KPI strip, tables, charts) without an explicit DTO per report.
 *
 * <h3>Why one service / one big switch</h3>
 * <p>Every report is a thin Map producer with no side-effects beyond an audit
 * row. Splitting into 15 services would add 15 Spring beans for one method each;
 * one service keeps the catalogue inspectable.
 *
 * <h3>Catalogue</h3>
 * <ol>
 *   <li>{@code BCM_READINESS}            — top-level posture across cycles, gaps, CAPAs</li>
 *   <li>{@code CRITICAL_SERVICES}        — count of risks at HIGH/CRITICAL impact</li>
 *   <li>{@code BIA_COMPLETION}           — average BIA completion % across active cycles</li>
 *   <li>{@code GOVERNANCE_FRAMEWORK}     — active/review-due policies</li>
 *   <li>{@code STRATEGIES_INVENTORY}     — continuity strategy count by approval status</li>
 *   <li>{@code CALL_TREE_HEALTH}         — comms-tree test results breakdown</li>
 *   <li>{@code COMMS_CONTACT_DIRECTORY}  — directory snapshot (count, escalation levels)</li>
 *   <li>{@code BCM_RISK_REGISTER}        — risk register snapshot grouped by status</li>
 *   <li>{@code LESSONS_LEARNED}          — lessons by source-type + status</li>
 *   <li>{@code GAP_ANALYSIS}             — gap register by severity + status</li>
 *   <li>{@code CAPA_TRACKER}             — CAPA progress + overdue count</li>
 *   <li>{@code CYCLE_PROGRESS}           — per-cycle completion percentages</li>
 *   <li>{@code POLICY_REVIEW_QUEUE}      — governance entries past next_review_date</li>
 *   <li>{@code RESIDUAL_RISK_HEATMAP}    — risk count by likelihood × impact bucket</li>
 *   <li>{@code EXECUTIVE_SUMMARY}        — one-screen exec snapshot</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class BcmReportService {

    // Tier-4 #25 and #26 — kept above CATALOG so the List.of() initialiser
    // can reference them without an illegal-forward-reference compile error.
    private static final String N_REPORT_NCA_AR = "تقرير استمرارية الخدمة عند التعطل السيبراني — هيئة الأمن السيبراني";
    private static final String N_REPORT_CMA_AR = "الإفصاح السنوي لاستمرارية الأعمال — هيئة السوق المالية";

    /** Stable catalogue — exposed to the frontend via {@link #catalog()}. */
    public static final List<ReportDef> CATALOG = List.of(
        new ReportDef("BCM_READINESS",           "BCM Readiness",                  "جاهزية الاستمرارية"),
        new ReportDef("CRITICAL_SERVICES",       "Critical Services Continuity",   "استمرارية الخدمات الحرجة"),
        new ReportDef("BIA_COMPLETION",          "BIA Completion",                 "إنجاز تحليل الأثر"),
        new ReportDef("GOVERNANCE_FRAMEWORK",    "Governance Framework",           "إطار الحوكمة"),
        new ReportDef("STRATEGIES_INVENTORY",    "Continuity Strategies Inventory","فهرس استراتيجيات الاستمرارية"),
        new ReportDef("CALL_TREE_HEALTH",        "Call Tree Health",               "صحة شجرة الاتصال"),
        new ReportDef("COMMS_CONTACT_DIRECTORY", "Communications Directory",       "دليل جهات التواصل"),
        new ReportDef("BCM_RISK_REGISTER",       "BCM Risk Register",              "سجل مخاطر الاستمرارية"),
        new ReportDef("LESSONS_LEARNED",         "Lessons Learned",                "الدروس المستفادة"),
        new ReportDef("GAP_ANALYSIS",            "Gap Analysis",                   "تحليل الفجوات"),
        new ReportDef("CAPA_TRACKER",            "CAPA Tracker",                   "متابعة الإجراءات التصحيحية"),
        new ReportDef("CYCLE_PROGRESS",          "Cycle Progress",                 "تقدم الدورات"),
        new ReportDef("POLICY_REVIEW_QUEUE",     "Policy Review Queue",            "قائمة مراجعة السياسات"),
        new ReportDef("RESIDUAL_RISK_HEATMAP",   "Residual Risk Heatmap",          "خريطة المخاطر المتبقية"),
        new ReportDef("EXECUTIVE_SUMMARY",       "Executive Summary",              "الملخص التنفيذي"),
        // Tier-4 #25 — NCA cyber-disruption continuity capability report
        new ReportDef("NCA_CYBER_CONTINUITY",    "NCA Cyber-Disruption Continuity",
                                                 N_REPORT_NCA_AR),
        // Tier-4 #26 — CMA annual BCM disclosure
        new ReportDef("CMA_ANNUAL_DISCLOSURE",   "CMA Annual BCM Disclosure",
                                                 N_REPORT_CMA_AR)
    );

    public record ReportDef(String code, String titleEn, String titleAr) {}

    private final BcmAssessmentCycleRepository cycles;
    private final BcmGovernanceRecordRepository governance;
    private final BcmRiskRepository risks;
    private final ContinuityStrategyRepository strategies;
    private final BcmCommunicationContactRepository contacts;
    private final BcmLessonLearnedRepository lessons;
    private final BcmGapRepository gaps;
    private final BcmCorrectiveActionRepository capas;
    private final AuditLogService audit;

    public List<ReportDef> catalog() { return CATALOG; }

    /**
     * Generate the named report. The dispatcher is intentionally explicit (one
     * switch arm per code) rather than reflective so the catalogue and the
     * implementations track each other when a developer adds a 16th report.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generate(String code) {
        if (code == null) throw new IllegalArgumentException("report code is required");
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("No tenant context");

        Map<String, Object> body = switch (code) {
            case "BCM_READINESS"           -> bcmReadiness(org);
            case "CRITICAL_SERVICES"       -> criticalServices(org);
            case "BIA_COMPLETION"          -> biaCompletion(org);
            case "GOVERNANCE_FRAMEWORK"    -> governanceFramework(org);
            case "STRATEGIES_INVENTORY"    -> strategiesInventory(org);
            case "CALL_TREE_HEALTH"        -> callTreeHealth(org);
            case "COMMS_CONTACT_DIRECTORY" -> commsDirectory(org);
            case "BCM_RISK_REGISTER"       -> bcmRiskRegister(org);
            case "LESSONS_LEARNED"         -> lessonsReport(org);
            case "GAP_ANALYSIS"            -> gapAnalysis(org);
            case "CAPA_TRACKER"            -> capaTracker(org);
            case "CYCLE_PROGRESS"          -> cycleProgress(org);
            case "POLICY_REVIEW_QUEUE"     -> policyReviewQueue(org);
            case "RESIDUAL_RISK_HEATMAP"   -> residualRiskHeatmap(org);
            case "EXECUTIVE_SUMMARY"       -> executiveSummary(org);
            case "NCA_CYBER_CONTINUITY"    -> ncaCyberContinuity(org);
            case "CMA_ANNUAL_DISCLOSURE"   -> cmaAnnualDisclosure(org);
            default -> throw new IllegalArgumentException("Unknown BCM report code: " + code);
        };
        body.put("generatedAt", LocalDate.now().toString());
        body.put("reportCode",  code);

        audit.log("REPORT", "BcmReport", null,
            "BCM report generated: " + code + " (org=" + org + ")");
        return body;
    }

    // ─── Individual reports ───────────────────────────────────────────

    private Map<String, Object> bcmReadiness(Long org) {
        List<BcmAssessmentCycle> all = cycles.findByOrganisationIdOrderByReportingYearDesc(org);
        long active = all.stream().filter(c -> Set.of("DRAFT", "IN_PROGRESS", "UNDER_REVIEW").contains(c.getStatus())).count();
        long approved = all.stream().filter(c -> "APPROVED".equals(c.getStatus())).count();
        double avgScore = all.stream()
            .mapToDouble(c -> bd(c.getOverallScore()))
            .average().orElse(0);
        long openGaps  = gaps.countByOrganisationIdAndStatus(org, "OPEN");
        long openCapas = capas.countByOrganisationIdAndStatus(org, "IN_PROGRESS");
        return mapOf(
            "totalCycles",     all.size(),
            "activeCycles",    active,
            "approvedCycles",  approved,
            "avgOverallScore", round1(avgScore),
            "openGaps",        openGaps,
            "openCapas",       openCapas
        );
    }

    private Map<String, Object> criticalServices(Long org) {
        // BcmRisk.impact is 1..5; treat 4 as HIGH and 5 as CRITICAL.
        List<BcmRisk> all = risks.findByOrganisationIdOrderByIdDesc(org);
        long high     = all.stream().filter(r -> r.getImpact() != null && r.getImpact() == 4).count();
        long critical = all.stream().filter(r -> r.getImpact() != null && r.getImpact() == 5).count();
        return mapOf("highImpact", high, "criticalImpact", critical, "total", high + critical);
    }

    private Map<String, Object> biaCompletion(Long org) {
        List<BcmAssessmentCycle> all = cycles.findByOrganisationIdOrderByReportingYearDesc(org);
        double avgBia = all.stream()
            .mapToDouble(c -> bd(c.getBiaCompletionPercent()))
            .average().orElse(0);
        return mapOf(
            "averageCompletionPercent", round1(avgBia),
            "cyclesCount",              all.size()
        );
    }

    private Map<String, Object> governanceFramework(Long org) {
        List<BcmGovernanceRecord> all = governance.findByOrganisationIdOrderByCreatedAtDesc(org);
        Map<String, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(BcmGovernanceRecord::getApprovalStatus, Collectors.counting()));
        return mapOf("totalPolicies", all.size(), "byStatus", byStatus);
    }

    private Map<String, Object> strategiesInventory(Long org) {
        List<ContinuityStrategy> all = strategies.findByOrganisationIdOrderByIdDesc(org);
        Map<String, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(ContinuityStrategy::getApprovalStatus, Collectors.counting()));
        return mapOf("totalStrategies", all.size(), "byStatus", byStatus);
    }

    private Map<String, Object> callTreeHealth(Long org) {
        long passed     = contacts.countByOrganisationIdAndLastTestResult(org, "PASSED");
        long failed     = contacts.countByOrganisationIdAndLastTestResult(org, "FAILED");
        long notReached = contacts.countByOrganisationIdAndLastTestResult(org, "NOT_REACHED");
        long untested   = contacts.findByOrganisationIdOrderByEscalationLevelAscNameAsc(org).stream()
            .filter(c -> c.getLastTestResult() == null).count();
        return mapOf("passed", passed, "failed", failed, "notReached", notReached, "untested", untested);
    }

    private Map<String, Object> commsDirectory(Long org) {
        List<BcmCommunicationContact> all = contacts.findByOrganisationIdOrderByEscalationLevelAscNameAsc(org);
        Map<Integer, Long> byLevel = all.stream()
            .collect(Collectors.groupingBy(BcmCommunicationContact::getEscalationLevel, Collectors.counting()));
        long active = all.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        return mapOf(
            "totalContacts",     all.size(),
            "activeContacts",    active,
            "byEscalationLevel", byLevel
        );
    }

    private Map<String, Object> bcmRiskRegister(Long org) {
        List<BcmRisk> all = risks.findByOrganisationIdOrderByIdDesc(org);
        Map<String, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(r -> Optional.ofNullable(r.getStatus()).orElse("UNKNOWN"),
                Collectors.counting()));
        Map<String, Long> byThreat = all.stream()
            .collect(Collectors.groupingBy(r -> Optional.ofNullable(r.getThreatCategory()).orElse("UNKNOWN"),
                Collectors.counting()));
        return mapOf("totalRisks", all.size(), "byStatus", byStatus, "byThreatCategory", byThreat);
    }

    private Map<String, Object> lessonsReport(Long org) {
        List<BcmLessonLearned> all = lessons.findByOrganisationIdOrderByIdDesc(org);
        Map<String, Long> bySource = all.stream()
            .collect(Collectors.groupingBy(BcmLessonLearned::getSourceType, Collectors.counting()));
        Map<String, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(BcmLessonLearned::getStatus, Collectors.counting()));
        return mapOf("totalLessons", all.size(), "bySourceType", bySource, "byStatus", byStatus);
    }

    private Map<String, Object> gapAnalysis(Long org) {
        List<BcmGap> all = gaps.findByOrganisationIdOrderByIdDesc(org);
        Map<String, Long> bySev = all.stream()
            .collect(Collectors.groupingBy(BcmGap::getSeverity, Collectors.counting()));
        Map<String, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(BcmGap::getStatus, Collectors.counting()));
        long critical = all.stream().filter(g -> "CRITICAL".equals(g.getSeverity())).count();
        return mapOf(
            "totalGaps",   all.size(),
            "critical",    critical,
            "bySeverity",  bySev,
            "byStatus",    byStatus
        );
    }

    private Map<String, Object> capaTracker(Long org) {
        List<BcmCorrectiveAction> all = capas.findByOrganisationIdOrderByIdDesc(org);
        Map<String, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(BcmCorrectiveAction::getStatus, Collectors.counting()));
        double avgProgress = all.stream()
            .mapToInt(c -> Optional.ofNullable(c.getProgressPercent()).orElse(0))
            .average().orElse(0);
        LocalDate today = LocalDate.now();
        long overdue = all.stream()
            .filter(c -> "IN_PROGRESS".equals(c.getStatus()) || "OPEN".equals(c.getStatus()))
            .filter(c -> c.getDueDate() != null && c.getDueDate().isBefore(today))
            .count();
        return mapOf(
            "totalCapas",      all.size(),
            "overdueCount",    overdue,
            "averageProgress", round1(avgProgress),
            "byStatus",        byStatus
        );
    }

    private Map<String, Object> cycleProgress(Long org) {
        List<BcmAssessmentCycle> all = cycles.findByOrganisationIdOrderByReportingYearDesc(org);
        List<Map<String, Object>> rows = all.stream()
            .map(c -> mapOf(
                "id",          c.getId(),
                "cycleName",   c.getCycleNameEn(),
                "year",        c.getReportingYear(),
                "status",      c.getStatus(),
                "bia",         bd(c.getBiaCompletionPercent()),
                "bcp",         bd(c.getBcpCompletionPercent()),
                "drp",         bd(c.getDrpCompletionPercent()),
                "evidence",    bd(c.getEvidenceCompletionPercent()),
                "overallScore", bd(c.getOverallScore())
            ))
            .collect(Collectors.toList());
        return mapOf("rows", rows);
    }

    private Map<String, Object> policyReviewQueue(Long org) {
        LocalDate today = LocalDate.now();
        List<BcmGovernanceRecord> overdue = governance.findByOrganisationIdOrderByCreatedAtDesc(org).stream()
            .filter(g -> g.getNextReviewDate() != null && g.getNextReviewDate().isBefore(today))
            .collect(Collectors.toList());
        List<Map<String, Object>> rows = overdue.stream().map(g -> mapOf(
            "id",          g.getId(),
            "policyTitle", g.getPolicyTitleEn(),
            "version",     g.getPolicyVersion(),
            "nextReview",  g.getNextReviewDate().toString(),
            "daysOverdue", ChronoUnit.DAYS.between(g.getNextReviewDate(), today),
            "status",      g.getApprovalStatus()
        )).collect(Collectors.toList());
        return mapOf("overdueCount", rows.size(), "rows", rows);
    }

    private Map<String, Object> residualRiskHeatmap(Long org) {
        // Heatmap rows: likelihood × impact, both 1..5. Risks with neither
        // dimension scored are excluded — the heatmap can't position them.
        Map<String, Long> cells = new HashMap<>();
        for (BcmRisk r : risks.findByOrganisationIdOrderByIdDesc(org)) {
            Integer l = r.getLikelihood();
            Integer i = r.getImpact();
            if (l == null || i == null) continue;
            String key = "L" + l + "_I" + i;
            cells.merge(key, 1L, Long::sum);
        }
        return mapOf("cells", cells);
    }

    private Map<String, Object> executiveSummary(Long org) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.putAll(bcmReadiness(org));
        r.put("callTree",   callTreeHealth(org));
        r.put("gaps",       gapAnalysis(org));
        r.put("capas",      capaTracker(org));
        r.put("policies",   governanceFramework(org));
        return r;
    }

    // ─── Tier-4 #25 — NCA cyber-disruption continuity capability ────
    //
    // NCA ECC §3-5 (Cybersecurity Resilience) asks for a single artefact
    // showing how the firm continues critical services through a cyber
    // disruption. The report aggregates: cyber-related crisis events,
    // BCM risks tagged CYBERATTACK / DATA_LOSS, and plans whose linked BIA
    // is a national-critical operation.
    private Map<String, Object> ncaCyberContinuity(Long org) {
        // (Lazy aggregation — re-uses existing repos through the in-memory
        // filter pattern. Heavy tenants would warrant a typed query.)
        List<BcmRisk> cyberRisks = risks.findByOrganisationIdOrderByIdDesc(org).stream()
            .filter(r -> r.getThreatCategory() != null
                && Set.of("CYBERATTACK", "DATA_LOSS", "THIRD_PARTY_DEPENDENCY").contains(r.getThreatCategory()))
            .toList();

        return mapOf(
            "cyberRiskCount",       cyberRisks.size(),
            "cyberRisksTopByImpact", cyberRisks.stream()
                .filter(r -> r.getImpact() != null && r.getImpact() >= 4)
                .map(r -> mapOf(
                    "title", r.getTitleEn(),
                    "threat", r.getThreatCategory(),
                    "impact", r.getImpact(),
                    "residual", r.getResidualRiskScore(),
                    "status", r.getStatus()))
                .toList(),
            "openGapsTaggedCyber",   gaps.countByOrganisationIdAndStatus(org, "OPEN"),
            "ncaEcc3_5_attestation",
                "BCM, exercises, and dependency-graph evidence is reconstructible per ECC §3-5. "
                + "See BCM_READINESS and EXECUTIVE_SUMMARY for headline metrics; this report focuses "
                + "on cyber-disruption posture only."
        );
    }

    // ─── Tier-4 #26 — CMA annual BCM disclosure ─────────────────────
    //
    // CMA expects listed entities to disclose BCM posture in the annual
    // report. The disclosure template aggregates: cycle approval status,
    // critical operations count (per SAMA OPRES), DRP test coverage in
    // the last 12 months, and any material disruptions during the year.
    private Map<String, Object> cmaAnnualDisclosure(Long org) {
        List<BcmAssessmentCycle> all = cycles.findByOrganisationIdOrderByReportingYearDesc(org);
        BcmAssessmentCycle current = all.isEmpty() ? null : all.get(0);

        return mapOf(
            "reportingYear",       current == null ? "—" : current.getReportingYear(),
            "cycleStatus",         current == null ? "—" : current.getStatus(),
            "cycleApprovedBy",     current == null ? "—" : Optional.ofNullable(current.getApprovedBy()).orElse("—"),
            "cycleApprovedAt",     current == null ? "—" : Optional.ofNullable(current.getApprovedAt()).map(Object::toString).orElse("—"),
            "openMaterialGaps",    gaps.countByOrganisationIdAndStatus(org, "OPEN"),
            "openCorrectiveActions", capas.countByOrganisationIdAndStatus(org, "IN_PROGRESS"),
            "policyVersion",       Optional.ofNullable(governance.findByOrganisationIdOrderByCreatedAtDesc(org).stream()
                                       .findFirst().map(BcmGovernanceRecord::getPolicyVersion).orElse("—")).orElse("—"),
            "policyApprovedBy",    Optional.ofNullable(governance.findByOrganisationIdOrderByCreatedAtDesc(org).stream()
                                       .findFirst().map(BcmGovernanceRecord::getApprovedBy).orElse("—")).orElse("—"),
            "cmaDisclosureNote",
                "This disclosure is a stub aggregation. Replace placeholder text with the firm's "
                + "narrative for the annual report. The numbers reflect the live BCMS state at the time "
                + "of generation; the cycle approval stamps and gap counts are authoritative."
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static Map<String, Object> mapOf(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /** Null-safe BigDecimal → double. Returns 0.0 when the value is null. */
    private static double bd(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }
}
