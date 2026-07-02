package com.tss.grc.module.sust.dashboard.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.cma.service.CmaDisclosureService;
import com.tss.grc.module.sust.evidence.service.SustainabilityEvidenceService;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
import com.tss.grc.module.sust.materiality.service.MaterialTopicService;
import com.tss.grc.module.sust.metrics.service.EsgMetricService;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import com.tss.grc.module.sust.sgi.service.SgiCommitmentService;
import com.tss.grc.module.sust.tcfd.service.TcfdItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Sustainability Dashboard aggregator (slice 11).
 *
 * <p>One read-only endpoint that composes the 10 underlying slice
 * services into a single executive view — saves the dashboard 8 round
 * trips on first paint. Also computes the ESG Overall Score: a
 * weighted composite (0..100) across GHG verification, SGI on-track
 * rate, CMA disclosure %, TCFD alignment %, CAPA closure rate,
 * evidence-approval rate, and climate-risk treatment rate.
 *
 * <p>Weights are tuned for Saudi-market emphasis on regulator-facing
 * disclosures (CMA + GHG verification dominate; Materiality is a
 * supporting signal not a scored input):
 * <pre>
 *   GHG verification rate   20
 *   SGI on-track rate       20
 *   CMA disclosed rate      20
 *   TCFD alignment          15
 *   CAPA closure rate       10
 *   Evidence approved rate  10
 *   Climate risks treated    5
 *   ────────────────────────────
 *   Total                  100
 * </pre>
 *
 * <p>Readiness band off the score:
 * {@code 80+ STRONG · 60+ ADEQUATE · 40+ DEVELOPING · <40 CRITICAL}.
 */
@Service
@RequiredArgsConstructor
public class SustainabilityDashboardService {

    private final GhgEmissionRecordService     ghgService;
    private final SgiCommitmentService         sgiService;
    private final CmaDisclosureService         cmaService;
    private final TcfdItemService              tcfdService;
    private final CorrectiveActionService      capaService;
    private final SustainabilityEvidenceService evidenceService;
    private final MaterialTopicService         materialityService;
    private final EsgMetricService             metricsService;
    // Slice 14-D — climate-risk aggregate replaces the previous
    // placeholder constant in climateTreatedRate().
    private final com.tss.grc.module.sust.climate.service.ClimateRiskOpportunityService climateService;
    private final OrganisationSettingsService settingsService;

    /**
     * Build the full dashboard payload. Returned shape is JSON-stable
     * so the frontend can introspect each sub-aggregate without
     * additional API calls.
     */
    public Map<String, Object> dashboard(Short year) {
        // Pull every aggregate. These calls are cheap (each is a single
        // findAll + in-memory rollup on the org-scoped slice).
        Map<String, Object> ghg          = ghgService.aggregate(year);
        Map<String, Object> ghgYoy       = ghgService.yoy(year);
        Map<String, Object> sgi          = sgiService.aggregate();
        Map<String, Object> cma          = cmaService.readiness(year);
        Map<String, Object> tcfd         = tcfdService.readiness(year);
        Map<String, Object> capa         = capaService.aggregate();
        Map<String, Object> evidence     = evidenceService.aggregate();
        Map<String, Object> materiality  = materialityService.generateMatrix(year);
        Map<String, Object> metrics      = metricsService.aggregate(year);

        // ── ESG Overall Score (0-100) ──────────────────────────────
        // Each component contributes its weight × normalised score.
        // Missing data → 0 contribution (conservative: a year with no
        // GHG records can't claim verification credit).
        BigDecimal ghgVerifPct      = ghgVerificationRate(year);     // 0..100
        BigDecimal sgiOnTrackPct    = sgiOnTrackRate(sgi);            // 0..100
        BigDecimal cmaPct           = asPct(cma.get("readinessPct"));
        BigDecimal tcfdPct          = asPct(tcfd.get("alignmentPct"));
        BigDecimal capaClosePct     = capaCloseRate(capa);            // 0..100
        BigDecimal evidencePct      = evidenceApprovedRate(evidence); // 0..100
        BigDecimal climateTreatPct  = climateTreatedRate(year);       // 0..100

        BigDecimal score =
              ghgVerifPct.multiply(new BigDecimal("0.20"))
            .add(sgiOnTrackPct.multiply(new BigDecimal("0.20")))
            .add(cmaPct.multiply(new BigDecimal("0.20")))
            .add(tcfdPct.multiply(new BigDecimal("0.15")))
            .add(capaClosePct.multiply(new BigDecimal("0.10")))
            .add(evidencePct.multiply(new BigDecimal("0.10")))
            .add(climateTreatPct.multiply(new BigDecimal("0.05")))
            .setScale(0, RoundingMode.HALF_UP);

        // S-B — the ADEQUATE / DEVELOPING band floors are tenant-configurable
        // via OrganisationSettings (defaults 60 / 40 live in the settings
        // schema). STRONG stays a fixed 80 ceiling — there is no setting for it.
        OrganisationSettings st = settingsService.getOrCreate();
        BigDecimal adequateFloor   = new BigDecimal(st.getEsgReadinessWarningThreshold());
        BigDecimal developingFloor = new BigDecimal(st.getEsgReadinessCriticalThreshold());
        String band = score.compareTo(new BigDecimal("80")) >= 0 ? "STRONG"
                   : score.compareTo(adequateFloor)   >= 0 ? "ADEQUATE"
                   : score.compareTo(developingFloor) >= 0 ? "DEVELOPING"
                   :                                          "CRITICAL";

        Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
        scoreBreakdown.put("ghgVerification",  ghgVerifPct);
        scoreBreakdown.put("sgiOnTrack",       sgiOnTrackPct);
        scoreBreakdown.put("cmaDisclosure",    cmaPct);
        scoreBreakdown.put("tcfdAlignment",    tcfdPct);
        scoreBreakdown.put("capaClosure",      capaClosePct);
        scoreBreakdown.put("evidenceApproved", evidencePct);
        scoreBreakdown.put("climateTreated",   climateTreatPct);

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("score", score);
        overall.put("band", band);
        overall.put("breakdown", scoreBreakdown);

        // ── Top ESG gaps — informational list for executive summary ─
        List<String> topGaps = topGapsList(ghg, sgi, cma, tcfd, capa, evidence);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("organisationId", TenantContext.getCurrentTenant());
        out.put("overall", overall);
        out.put("ghg", ghg);
        out.put("ghgYoy", ghgYoy);
        out.put("sgi", sgi);
        out.put("cma", cma);
        out.put("tcfd", tcfd);
        out.put("capa", capa);
        out.put("evidence", evidence);
        out.put("materiality", materiality);
        out.put("metrics", metrics);
        out.put("topGaps", topGaps);
        return out;
    }

    // ── Score components ─────────────────────────────────────────────

    /** Verified GHG records / total. Counts every status as denominator. */
    private BigDecimal ghgVerificationRate(Short year) {
        // Lean on the existing aggregate keyed result if available; the
        // GHG aggregate exposes recordCount but not verification rate.
        // Pull a slim count via the service's page helper.
        var page = ghgService.listByYear(year, org.springframework.data.domain.PageRequest.of(0, 1000));
        long total = page.getTotalElements();
        if (total == 0) return BigDecimal.ZERO;
        long verified = page.getContent().stream().filter(r -> Boolean.TRUE.equals(r.getVerified())).count();
        return new BigDecimal(verified * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    /** Active SGI commitments on-track rate. */
    @SuppressWarnings("unchecked")
    private BigDecimal sgiOnTrackRate(Map<String, Object> sgi) {
        Object byTierObj = sgi.get("byTier");
        if (!(byTierObj instanceof Map<?, ?> byTier)) return BigDecimal.ZERO;
        int onTrack = asInt(((Map<String, Object>) byTier).get("ON_TRACK"));
        int atRisk  = asInt(((Map<String, Object>) byTier).get("AT_RISK"));
        int delayed = asInt(((Map<String, Object>) byTier).get("DELAYED"));
        int total = onTrack + atRisk + delayed;
        if (total == 0) return BigDecimal.ZERO;
        return new BigDecimal(onTrack * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    /** (closed + verified) / total active corrective actions. */
    private BigDecimal capaCloseRate(Map<String, Object> capa) {
        int open       = asInt(capa.get("open"));
        int inProgress = asInt(capa.get("inProgress"));
        int completed  = asInt(capa.get("completed"));
        int verified   = asInt(capa.get("verified"));
        int total = open + inProgress + completed + verified;
        if (total == 0) return new BigDecimal("100");  // no open work = full credit
        int closedish = verified;  // VERIFIED is the closeable-state proxy
        return new BigDecimal(closedish * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    /** Approved evidence / total. */
    private BigDecimal evidenceApprovedRate(Map<String, Object> evidence) {
        int total = asInt(evidence.get("total"));
        if (total == 0) return BigDecimal.ZERO;
        @SuppressWarnings("unchecked")
        Map<String, Object> byStatus = (Map<String, Object>) evidence.get("byStatus");
        int approved = asInt(byStatus.get("APPROVED"));
        return new BigDecimal(approved * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Climate risks marked TREATED or CLOSED / all. Reads from the
     * ClimateRiskOpportunityService aggregate (slice 14-D). Replaces
     * the previous placeholder constant.
     *
     * <p>The {@code year} parameter is currently unused — climate
     * risks aren't year-scoped in V55. Kept on the signature so the
     * caller can pass through the dashboard year without a branch.
     */
    private BigDecimal climateTreatedRate(Short year) {
        Map<String, Object> agg = climateService.aggregate();
        return asPct(agg.get("treatedRatePct"));
    }

    // ── Top gaps list ────────────────────────────────────────────────

    /**
     * Order top gaps by criticality. Each entry is a bilingual-ready
     * string the frontend can render directly in the executive summary
     * panel. Bilingual rendering is intentionally NOT done here so the
     * dashboard endpoint returns a single canonical wire format.
     */
    private List<String> topGapsList(
            Map<String, Object> ghg, Map<String, Object> sgi,
            Map<String, Object> cma, Map<String, Object> tcfd,
            Map<String, Object> capa, Map<String, Object> evidence) {
        List<String> gaps = new ArrayList<>();

        // Rule 1 — Scope 3 partial coverage
        if (Boolean.TRUE.equals(ghg.get("scope3PartialFlag"))) {
            gaps.add("SCOPE_3_PARTIAL_COVERAGE");
        }
        // Rule 7 — TCFD Strategy resilience gap
        if (Boolean.TRUE.equals(tcfd.get("strategyResilienceGap"))) {
            gaps.add("TCFD_STRATEGY_RESILIENCE_GAP");
        }
        // CAPA overdue
        if (asInt(capa.get("overdue")) > 0) {
            gaps.add("CAPA_OVERDUE_" + asInt(capa.get("overdue")));
        }
        // Evidence expiring within 30 days
        if (asInt(evidence.get("expiringSoon30d")) > 0) {
            gaps.add("EVIDENCE_EXPIRING_" + asInt(evidence.get("expiringSoon30d")));
        }
        // CMA Partial / Not Disclosed
        @SuppressWarnings("unchecked")
        Map<String, Object> cmaByStatus = (Map<String, Object>) cma.get("byStatus");
        if (cmaByStatus != null) {
            int partial = asInt(cmaByStatus.get("PARTIAL"));
            int notDis  = asInt(cmaByStatus.get("NOT_DISCLOSED"));
            if (partial + notDis > 0) gaps.add("CMA_INCOMPLETE_" + (partial + notDis));
        }
        // SGI at-risk + delayed
        @SuppressWarnings("unchecked")
        Map<String, Object> sgiByTier = (Map<String, Object>) sgi.get("byTier");
        if (sgiByTier != null) {
            int atRisk  = asInt(sgiByTier.get("AT_RISK"));
            int delayed = asInt(sgiByTier.get("DELAYED"));
            if (atRisk + delayed > 0) gaps.add("SGI_BEHIND_" + (atRisk + delayed));
        }
        return gaps;
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private static int asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
    private static BigDecimal asPct(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
