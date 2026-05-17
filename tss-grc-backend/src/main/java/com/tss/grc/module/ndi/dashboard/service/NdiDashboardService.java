package com.tss.grc.module.ndi.dashboard.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
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
import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import com.tss.grc.module.ndi.submission.repository.NdiSubmissionPackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NDI Dashboard service — slice 12.
 *
 * <p>Pure aggregator. Returns the 14 widgets defined in the spec for
 * a single payload that the frontend renders in one panel without
 * round-trips per tab.
 */
@Service
@Transactional(readOnly = true)
public class NdiDashboardService {

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

    public NdiDashboardService(NdiAssessmentCycleRepository cycles,
                               NdiMaturityAssessmentRepository maturity,
                               NdiComplianceAssessmentRepository compliance,
                               NdiOperationalExcellenceKpiRepository opex,
                               NdiEvidenceItemRepository evidence,
                               NdiGapRepository gaps,
                               NdiCorrectiveActionRepository capas,
                               NdiImprovementPlanRepository plans,
                               NdiSubmissionPackageRepository submissions,
                               NdiGovernanceDomainService domainService) {
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
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    public record DashboardSnapshot(
        Long cycleId,
        String cycleNameEn,
        String cycleNameAr,
        Short reportingYear,
        String cycleStatus,
        BigDecimal overallNdiPct,
        BigDecimal maturityScorePct,
        BigDecimal compliancePct,
        BigDecimal opexScorePct,
        int totalDomains,
        int placeholderDomains,
        int totalMaturity, int maturityApproved,
        int totalCompliance, int complianceApproved,
        int totalOpex, int opexAchieved, int opexBehindPlan,
        int totalEvidence, int evidenceActive, int evidenceExpiringSoon, int evidenceExpired,
        int totalGaps, int criticalOpen, int gapsResolved,
        int totalCapas, int capasCompleted, int capasOverdue,
        int totalPlans, int plansInFlight, int plansCompleted,
        int submissionsTotal, int submissionsAcknowledged,
        List<DomainCard> topUnderperforming,
        List<UpcomingItem> upcoming,
        Map<String, Object> latestSubmission
    ) {}

    public record DomainCard(
        String code, String nameEn, String nameAr,
        BigDecimal weight, BigDecimal blendedScorePct, boolean isPlaceholder
    ) {}

    public record UpcomingItem(
        String type, String code, String titleEn, String titleAr,
        LocalDate dueDate, int daysUntil, String severity
    ) {}

    public DashboardSnapshot snapshot(Long cycleId) {
        Long o = org();

        NdiAssessmentCycle cycle = cycles.findByIdAndOrganisationId(cycleId, o)
            .orElseThrow(() -> new IllegalArgumentException("Cycle not found: " + cycleId));

        CycleRollup rollup = domainService.rollupForCycle(cycleId);

        List<NdiMaturityAssessment> mat = maturity.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiComplianceAssessment> cmp = compliance.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiOperationalExcellenceKpi> ops = opex.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiEvidenceItem> evs = evidence.findByOrganisationIdAndCycleId(o, cycleId);
        List<NdiGap> gs = gaps.findByOrganisationIdAndCycleId(o, cycleId);
        Set<Long> gapIds = gs.stream().map(NdiGap::getId).collect(Collectors.toSet());
        List<NdiCorrectiveAction> cs = capas.findByOrganisationId(o).stream()
            .filter(c -> gapIds.contains(c.getGapId())).toList();
        List<NdiImprovementPlan> pls = plans.findByOrganisationIdAndCycleIdOrderByPeriodStartDesc(o, cycleId);
        List<NdiSubmissionPackage> subs = submissions.findByOrganisationIdAndCycleIdOrderByVersionDesc(o, cycleId);

        BigDecimal matAvg = avgLevelToPct(mat);
        BigDecimal cmpAvg = avgCompliance(cmp);
        BigDecimal opAvg  = avgOpexPct(ops);

        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(60);
        int expiringSoon = (int) evs.stream()
            .filter(e -> "ACTIVE".equals(e.getStatus())
                && e.getExpiryDate() != null
                && !e.getExpiryDate().isBefore(today)
                && e.getExpiryDate().isBefore(horizon))
            .count();
        int expired = (int) evs.stream().filter(e -> "EXPIRED".equals(e.getStatus())).count();
        int active  = (int) evs.stream().filter(e -> "ACTIVE".equals(e.getStatus())).count();

        int matAppr = (int) mat.stream().filter(m -> "APPROVED".equals(m.getStatus())).count();
        int cmpAppr = (int) cmp.stream().filter(c -> "APPROVED".equals(c.getLifecycleStatus())).count();
        int opAch   = (int) ops.stream().filter(k -> "ACHIEVED".equals(k.getStatus())).count();
        int opBeh   = (int) ops.stream().filter(k -> "BEHIND_PLAN".equals(k.getStatus())).count();

        int criticalOpen = (int) gs.stream()
            .filter(g -> "CRITICAL".equals(g.getSeverity())
                && !"RESOLVED".equals(g.getStatus())
                && !"CLOSED".equals(g.getStatus())
                && !"ACCEPTED_RISK".equals(g.getStatus()))
            .count();
        int gapsResolved = (int) gs.stream()
            .filter(g -> "RESOLVED".equals(g.getStatus()) || "CLOSED".equals(g.getStatus()))
            .count();
        int capasDone = (int) cs.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count();
        int capasOverdue = (int) cs.stream()
            .filter(c -> c.getTargetDate() != null
                      && c.getTargetDate().isBefore(today)
                      && !"COMPLETED".equals(c.getStatus()))
            .count();

        int plansInFlight = (int) pls.stream().filter(p -> "IN_FLIGHT".equals(p.getStatus())).count();
        int plansCompleted = (int) pls.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        int subsAck = (int) subs.stream().filter(p -> "ACKNOWLEDGED".equals(p.getStatus())).count();

        int placeholderDomains = (int) rollup.domains().stream().filter(d -> d.isPlaceholder()).count();

        // Top 3 underperforming domains (lowest blendedScorePct, non-null).
        List<DomainCard> bottom = rollup.domains().stream()
            .filter(d -> d.blendedScorePct() != null)
            .sorted(Comparator.comparing(d -> d.blendedScorePct()))
            .limit(3)
            .map(d -> new DomainCard(d.code(), d.nameEn(), d.nameAr(),
                d.weight(), d.blendedScorePct(), d.isPlaceholder()))
            .toList();

        // Upcoming: evidence expiring + CAPAs due, ranked by date, limited to 10.
        List<UpcomingItem> upcoming = new ArrayList<>();
        for (NdiEvidenceItem e : evs) {
            if ("ACTIVE".equals(e.getStatus()) && e.getExpiryDate() != null
                && !e.getExpiryDate().isBefore(today)
                && e.getExpiryDate().isBefore(horizon)) {
                upcoming.add(new UpcomingItem(
                    "evidence",
                    e.getEvidenceCode(),
                    e.getTitleEn(),
                    e.getTitleAr(),
                    e.getExpiryDate(),
                    (int) (e.getExpiryDate().toEpochDay() - today.toEpochDay()),
                    null
                ));
            }
        }
        for (NdiCorrectiveAction c : cs) {
            if (!"COMPLETED".equals(c.getStatus()) && c.getTargetDate() != null
                && !c.getTargetDate().isBefore(today)
                && c.getTargetDate().isBefore(horizon)) {
                upcoming.add(new UpcomingItem(
                    "capa",
                    c.getActionCode(),
                    c.getActionEn(),
                    c.getActionAr(),
                    c.getTargetDate(),
                    (int) (c.getTargetDate().toEpochDay() - today.toEpochDay()),
                    null
                ));
            }
        }
        upcoming.sort(Comparator.comparing(UpcomingItem::dueDate));
        if (upcoming.size() > 10) upcoming = upcoming.subList(0, 10);

        Map<String, Object> latest = new LinkedHashMap<>();
        if (!subs.isEmpty()) {
            NdiSubmissionPackage p = subs.get(0);
            latest.put("packageCode", p.getPackageCode());
            latest.put("version", p.getVersion());
            latest.put("status", p.getStatus());
            latest.put("overallNdiPct", p.getOverallNdiPct());
            latest.put("checksum", p.getManifestChecksum());
            latest.put("sdaiaReference", p.getSdaiaReference());
            latest.put("submittedAt", p.getSubmittedAt());
            latest.put("acknowledgedAt", p.getAcknowledgedAt());
        }

        return new DashboardSnapshot(
            cycleId,
            cycle.getCycleNameEn(),
            cycle.getCycleNameAr(),
            cycle.getReportingYear(),
            cycle.getStatus(),
            rollup.overallNdiPct(),
            matAvg, cmpAvg, opAvg,
            rollup.domains().size(), placeholderDomains,
            mat.size(), matAppr,
            cmp.size(), cmpAppr,
            ops.size(), opAch, opBeh,
            evs.size(), active, expiringSoon, expired,
            gs.size(), criticalOpen, gapsResolved,
            cs.size(), capasDone, capasOverdue,
            pls.size(), plansInFlight, plansCompleted,
            subs.size(), subsAck,
            bottom, upcoming, latest);
    }

    private BigDecimal avgLevelToPct(List<NdiMaturityAssessment> rows) {
        int n = 0; int sum = 0;
        for (NdiMaturityAssessment m : rows) {
            if (m.getCurrentLevel() != null) { sum += m.getCurrentLevel(); n++; }
        }
        if (n == 0) return null;
        return BigDecimal.valueOf((double) sum * 20.0 / n)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal avgCompliance(List<NdiComplianceAssessment> rows) {
        int n = 0; BigDecimal sum = BigDecimal.ZERO;
        for (NdiComplianceAssessment c : rows) {
            if (c.getComplianceScore() != null) { sum = sum.add(c.getComplianceScore()); n++; }
        }
        if (n == 0) return null;
        return sum.divide(BigDecimal.valueOf(n), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal avgOpexPct(List<NdiOperationalExcellenceKpi> rows) {
        int n = 0; BigDecimal sum = BigDecimal.ZERO;
        for (NdiOperationalExcellenceKpi k : rows) {
            if (k.getActual() == null || k.getTarget() == null) continue;
            BigDecimal baseline = k.getBaseline() == null ? BigDecimal.ZERO : k.getBaseline();
            BigDecimal denom = k.getTarget().subtract(baseline);
            if (denom.signum() == 0) {
                if (k.getActual().compareTo(k.getTarget()) == 0) {
                    sum = sum.add(BigDecimal.valueOf(100)); n++;
                }
                continue;
            }
            BigDecimal raw = k.getActual().subtract(baseline)
                .divide(denom, 4, java.math.RoundingMode.HALF_UP);
            if (raw.compareTo(BigDecimal.ZERO) < 0) raw = BigDecimal.ZERO;
            if (raw.compareTo(BigDecimal.ONE) > 0)  raw = BigDecimal.ONE;
            sum = sum.add(raw.multiply(BigDecimal.valueOf(100))); n++;
        }
        if (n == 0) return null;
        return sum.divide(BigDecimal.valueOf(n), 2, java.math.RoundingMode.HALF_UP);
    }
}
