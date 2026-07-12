package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsRecord;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsRecordRepository;
import com.tss.grc.module.sust.climate.repository.TcfdClimateScenarioRepository;
import com.tss.grc.module.sust.finance.entity.CarbonCredit;
import com.tss.grc.module.sust.finance.entity.EsgTarget;
import com.tss.grc.module.sust.finance.entity.PortfolioAsset;
import com.tss.grc.module.sust.finance.entity.TaxonomyEntry;
import com.tss.grc.module.sust.finance.repository.CarbonCreditRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetRepository;
import com.tss.grc.module.sust.finance.repository.PortfolioAssetRepository;
import com.tss.grc.module.sust.finance.repository.TaxonomyEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Green-Finance uplift G3 (plan §5.1–§5.3) — the Green Portfolio (المحفظة الخضراء)
 * and the voluntary carbon-credit ledger. Rules enforced here:
 * <ul>
 *   <li>Tagging SoD: the tag proposer cannot decide the tag (409).</li>
 *   <li>Tags must reference an active taxonomy code (platform set org 0 + tenant CUSTOM).</li>
 *   <li>GAR numerator = ELIGIBLE exposure; denominator = all assessed (decided) exposure.</li>
 *   <li>Weighted PCAF data-quality = Σ(DQ × outstanding) / Σ(outstanding) over financed-emissions records.</li>
 *   <li>Credit retirement requires a registry certificate reference + claim purpose (400 otherwise).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GreenPortfolioService {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<String> DECIDABLE = Set.of("ELIGIBLE", "TRANSITION", "INELIGIBLE");

    private final TaxonomyEntryRepository taxonomy;
    private final PortfolioAssetRepository assets;
    private final CarbonCreditRepository credits;
    private final FinancedEmissionsRecordRepository financedEmissions;
    private final TcfdClimateScenarioRepository scenarios;
    private final EsgTargetRepository esgTargets;
    private final com.tss.grc.module.sust.settings.service.OrganisationSettingsService settings;
    private final AuditLogService audit;

    /** Fallback when the tenant setting is unset (V295 made the cap configurable). */
    private static final BigDecimal OFFSET_CAP_PCT = BigDecimal.valueOf(10);

    // ── taxonomy ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaxonomyEntry> listTaxonomy() {
        return taxonomy.findByOrganisationIdInAndIsActiveTrueAndIsDeletedFalseOrderBySourceAscCodeAsc(
            List.of(0L, org()));
    }

    // ── portfolio assets + tagging (maker-checker) ───────────────────────

    @Transactional(readOnly = true)
    public List<PortfolioAsset> listAssets() {
        return assets.findByOrganisationIdAndIsDeletedFalseOrderByAssetCodeAsc(org());
    }

    @Transactional
    public PortfolioAsset createAsset(String assetCode, String nameEn, String nameAr,
                                      String sector, BigDecimal exposureSarMn) {
        Long org = org();
        if (assets.existsByOrganisationIdAndAssetCodeAndIsDeletedFalse(org, assetCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset code already exists: " + assetCode);
        }
        PortfolioAsset a = new PortfolioAsset();
        a.setOrganisationId(org);
        a.setAssetCode(assetCode);
        a.setNameEn(nameEn);
        a.setNameAr(nameAr);
        a.setSector(sector);
        a.setExposureSarMn(exposureSarMn);
        PortfolioAsset saved = assets.save(a);
        audit.log("CREATE", "PortfolioAsset", saved.getId(), "Portfolio asset created: " + assetCode);
        return saved;
    }

    /** 1st line proposes a tag (taxonomy code + requested eligibility). */
    @Transactional
    public PortfolioAsset proposeTag(Long id, String taxonomyCode, String requestedStatus) {
        PortfolioAsset a = mustAsset(id);
        if (!DECIDABLE.contains(requestedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "requestedStatus must be ELIGIBLE/TRANSITION/INELIGIBLE");
        }
        if (!taxonomy.existsByOrganisationIdInAndCodeAndIsActiveTrueAndIsDeletedFalse(
                List.of(0L, org()), taxonomyCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown taxonomy code: " + taxonomyCode);
        }
        a.setTaxonomyCode(taxonomyCode);
        a.setProposedStatus(requestedStatus);
        a.setEligibilityStatus("PROPOSED");
        a.setProposedBy(GreenFinanceService.currentUserId());
        a.setApprovedBy(null);
        a.setDecidedAt(null);
        PortfolioAsset saved = assets.save(a);
        audit.log("UPDATE", "PortfolioAsset", saved.getId(),
            "Green tag proposed: " + taxonomyCode + " -> " + requestedStatus);
        return saved;
    }

    /** 2nd line decides the tag — proposer ≠ approver (SoD). */
    @Transactional
    public PortfolioAsset decideTag(Long id, boolean approve) {
        PortfolioAsset a = mustAsset(id);
        if (!"PROPOSED".equals(a.getEligibilityStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending tag proposal on this asset");
        }
        Long approver = GreenFinanceService.currentUserId();
        if (approver != null && approver.equals(a.getProposedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Self-approval blocked: tag proposer and approver must differ (SoD)");
        }
        a.setEligibilityStatus(approve ? a.getProposedStatus() : "UNASSESSED");
        if (!approve) { a.setTaxonomyCode(null); a.setProposedStatus(null); }
        a.setApprovedBy(approver);
        a.setDecidedAt(LocalDateTime.now());
        PortfolioAsset saved = assets.save(a);
        audit.log(approve ? "APPROVE" : "REJECT", "PortfolioAsset", saved.getId(),
            "Green tag " + (approve ? "approved: " + saved.getEligibilityStatus() : "rejected"));
        return saved;
    }

    // ── V293: financed-emissions ↔ asset linkage (plan §5.1) ─────────────

    /** Link a PCAF financed-emissions record to a portfolio asset (re-link allowed). */
    @Transactional
    public FinancedEmissionsRecord linkEmissionRecord(Long assetId, Long recordId) {
        Long org = org();
        PortfolioAsset a = mustAsset(assetId);
        FinancedEmissionsRecord fe = financedEmissions.findByOrganisationIdAndIdAndIsDeletedFalse(org, recordId)
            .orElseThrow(() -> new EntityNotFoundException("Financed-emissions record not found: " + recordId));
        fe.setAssetRef(a.getId());
        FinancedEmissionsRecord saved = financedEmissions.save(fe);
        audit.log("UPDATE", "FinancedEmissionsRecord", saved.getId(),
            "Linked to portfolio asset " + a.getAssetCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FinancedEmissionsRecord> assetEmissions(Long assetId) {
        mustAsset(assetId);
        return financedEmissions.findByOrganisationIdAndAssetRefAndIsDeletedFalseOrderByReportingYearDesc(org(), assetId);
    }

    // ── V293: climate overlay (plan §5.2) ────────────────────────────────

    public record OverlayRow(String taxonomyCode, BigDecimal exposureSarMn, BigDecimal stressedLossSarMn) {}
    public record ClimateOverlay(Long scenarioId, String scenarioType, BigDecimal scenarioLossRatePct,
                                 List<OverlayRow> byCategory,
                                 BigDecimal totalEligibleExposureSarMn, BigDecimal totalStressedLossSarMn) {}

    /** Applies the scenario's realised loss rate (expected loss ÷ portfolio at risk)
     *  across the eligible portfolio's taxonomy segments. */
    @Transactional(readOnly = true)
    public ClimateOverlay climateOverlay(Long scenarioId) {
        Long org = org();
        TcfdClimateScenario s = scenarios.findById(scenarioId)
            .filter(x -> org.equals(x.getOrganisationId()) && !Boolean.TRUE.equals(x.getIsDeleted()))
            .orElseThrow(() -> new EntityNotFoundException("Climate scenario not found: " + scenarioId));
        if (s.getPortfolioAtRiskSarBillions() == null || s.getPortfolioAtRiskSarBillions().signum() == 0
                || s.getExpectedLossSarMillions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Scenario has no computed loss figures yet (add sector impacts first)");
        }
        BigDecimal lossRatePct = s.getExpectedLossSarMillions()
            .divide(s.getPortfolioAtRiskSarBillions().multiply(BigDecimal.valueOf(1000)), MC)
            .multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);

        Map<String, BigDecimal> mix = new TreeMap<>();
        BigDecimal totalEligible = BigDecimal.ZERO;
        for (PortfolioAsset a : assets.findByOrganisationIdAndIsDeletedFalseOrderByAssetCodeAsc(org)) {
            if (!"ELIGIBLE".equals(a.getEligibilityStatus()) || a.getTaxonomyCode() == null) continue;
            mix.merge(a.getTaxonomyCode(), a.getExposureSarMn(), BigDecimal::add);
            totalEligible = totalEligible.add(a.getExposureSarMn());
        }
        List<OverlayRow> rows = mix.entrySet().stream()
            .map(e -> new OverlayRow(e.getKey(), e.getValue().setScale(4, RoundingMode.HALF_UP),
                e.getValue().multiply(lossRatePct, MC).divide(HUNDRED, MC).setScale(4, RoundingMode.HALF_UP)))
            .toList();
        BigDecimal totalLoss = totalEligible.multiply(lossRatePct, MC).divide(HUNDRED, MC)
            .setScale(4, RoundingMode.HALF_UP);
        return new ClimateOverlay(s.getId(), s.getScenarioType(), lossRatePct, rows,
            totalEligible.setScale(4, RoundingMode.HALF_UP), totalLoss);
    }

    // ── portfolio summary (§5.2 calculations) ────────────────────────────

    public record PortfolioSummary(
        BigDecimal totalExposureSarMn,
        BigDecimal assessedExposureSarMn,
        BigDecimal eligibleExposureSarMn,
        BigDecimal greenAssetRatioPct,
        Map<String, BigDecimal> categoryMixSarMn,
        BigDecimal weightedDataQuality,
        BigDecimal financedEmissionsTco2e,
        BigDecimal assetLinkedEmissionsTco2e,
        int assets, int eligibleAssets, int pendingTags) {}

    @Transactional(readOnly = true)
    public PortfolioSummary summary() {
        Long org = org();
        List<PortfolioAsset> all = assets.findByOrganisationIdAndIsDeletedFalseOrderByAssetCodeAsc(org);

        BigDecimal total = BigDecimal.ZERO, assessed = BigDecimal.ZERO, eligible = BigDecimal.ZERO;
        Map<String, BigDecimal> mix = new TreeMap<>();
        int eligibleCount = 0, pending = 0;
        for (PortfolioAsset a : all) {
            total = total.add(a.getExposureSarMn());
            String st = a.getEligibilityStatus();
            if ("PROPOSED".equals(st)) pending++;
            if (DECIDABLE.contains(st)) assessed = assessed.add(a.getExposureSarMn());
            if ("ELIGIBLE".equals(st)) {
                eligible = eligible.add(a.getExposureSarMn());
                eligibleCount++;
                if (a.getTaxonomyCode() != null) {
                    mix.merge(a.getTaxonomyCode(), a.getExposureSarMn(), BigDecimal::add);
                }
            }
        }
        BigDecimal gar = assessed.signum() == 0 ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            : eligible.divide(assessed, MC).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);

        // Weighted PCAF DQ + financed emissions over the latest reporting year's records.
        List<FinancedEmissionsRecord> fes =
            financedEmissions.findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(org);
        Integer latestYear = fes.isEmpty() ? null : fes.get(0).getReportingYear();
        BigDecimal dqWeighted = BigDecimal.ZERO, outstanding = BigDecimal.ZERO, feTotal = BigDecimal.ZERO;
        BigDecimal linkedTotal = BigDecimal.ZERO;
        for (FinancedEmissionsRecord fe : fes) {
            if (latestYear != null && !latestYear.equals(fe.getReportingYear())) continue;
            BigDecimal out = fe.getOutstandingAmountSarMillions() == null ? BigDecimal.ZERO : fe.getOutstandingAmountSarMillions();
            int dq = fe.getDataQualityScore() == null ? 5 : fe.getDataQualityScore();
            dqWeighted = dqWeighted.add(out.multiply(BigDecimal.valueOf(dq)));
            outstanding = outstanding.add(out);
            if (fe.getTotalFinancedEmissionsTco2e() != null) {
                feTotal = feTotal.add(fe.getTotalFinancedEmissionsTco2e());
                if (fe.getAssetRef() != null) linkedTotal = linkedTotal.add(fe.getTotalFinancedEmissionsTco2e());
            }
        }
        BigDecimal wdq = outstanding.signum() == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : dqWeighted.divide(outstanding, MC).setScale(2, RoundingMode.HALF_UP);

        return new PortfolioSummary(
            total.setScale(4, RoundingMode.HALF_UP),
            assessed.setScale(4, RoundingMode.HALF_UP),
            eligible.setScale(4, RoundingMode.HALF_UP),
            gar, mix, wdq, feTotal.setScale(4, RoundingMode.HALF_UP),
            linkedTotal.setScale(4, RoundingMode.HALF_UP),
            all.size(), eligibleCount, pending);
    }

    // ── carbon credits (§5.3) ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CarbonCredit> listCredits() {
        return credits.findByOrganisationIdAndIsDeletedFalseOrderByVintageYearDesc(org());
    }

    @Transactional
    public CarbonCredit purchaseCredit(CarbonCredit c) {
        c.setOrganisationId(org());
        c.setStatus("PURCHASED");
        CarbonCredit saved = credits.save(c);
        audit.log("CREATE", "CarbonCredit", saved.getId(),
            "Carbon credits purchased: " + c.getQuantityTco2e() + " tCO2e (" + c.getRegistry() + ")");
        return saved;
    }

    /** Retirement requires the registry certificate + claim purpose (REG-11 integrity). */
    @Transactional
    public CarbonCredit retireCredit(Long id, String certificateRef, String purpose) {
        CarbonCredit c = credits.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Carbon credit not found: " + id));
        if (!"PURCHASED".equals(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit already " + c.getStatus());
        }
        if (certificateRef == null || certificateRef.isBlank() || purpose == null || purpose.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Retirement requires a registry certificate reference and a claim purpose");
        }
        enforceOffsetCap(c);
        c.setStatus("RETIRED");
        c.setRetirementDate(LocalDate.now());
        c.setCertificateRef(certificateRef);
        c.setRetirementPurpose(purpose);
        CarbonCredit saved = credits.save(c);
        audit.log("TRANSITION", "CarbonCredit", saved.getId(),
            "Carbon credits retired: " + c.getQuantityTco2e() + " tCO2e, cert " + certificateRef);
        return saved;
    }

    /**
     * V293 (plan §5.3 / decision #4, anti-greenwashing): offsets are only claimable
     * against a DISCLOSED gross reduction — retired credits may not exceed
     * {@value}% … i.e. {@link #OFFSET_CAP_PCT}% of Σ(baseline − target) over the
     * org's ACTIVE GHG reduction targets. No reduction target ⇒ no retirement.
     */
    private void enforceOffsetCap(CarbonCredit candidate) {
        Long org = org();
        BigDecimal grossReduction = BigDecimal.ZERO;
        for (EsgTarget t : esgTargets.findByOrganisationIdAndIsDeletedFalseOrderByTargetCodeAsc(org)) {
            if (!"ACTIVE".equals(t.getStatus()) || !t.getScope().startsWith("GHG")) continue;
            if (t.getTargetValue().compareTo(t.getBaselineValue()) >= 0) continue;
            grossReduction = grossReduction.add(t.getBaselineValue().subtract(t.getTargetValue()));
        }
        if (grossReduction.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Offset blocked: no ACTIVE gross GHG-reduction target is disclosed — "
                    + "offsets may only supplement disclosed reductions (REG-11 integrity)");
        }
        BigDecimal retired = candidate.getQuantityTco2e();
        for (CarbonCredit c : credits.findByOrganisationIdAndIsDeletedFalseOrderByVintageYearDesc(org)) {
            if ("RETIRED".equals(c.getStatus()) && c.getQuantityTco2e() != null && !c.getId().equals(candidate.getId())) {
                retired = retired.add(c.getQuantityTco2e());
            }
        }
        // V295 (decision #4) — the cap % is a tenant setting; fallback 10.
        BigDecimal capPct = settings.getOrCreate().getCarbonOffsetCapPct();
        if (capPct == null) capPct = OFFSET_CAP_PCT;
        BigDecimal cap = grossReduction.multiply(capPct, MC).divide(HUNDRED, MC);
        if (retired.compareTo(cap) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Offset cap exceeded: retiring " + candidate.getQuantityTco2e() + " tCO2e would take total retired to "
                    + retired.setScale(4, RoundingMode.HALF_UP) + " tCO2e, above " + capPct
                    + "% of the disclosed gross reduction (" + cap.setScale(4, RoundingMode.HALF_UP) + " tCO2e)");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private PortfolioAsset mustAsset(Long id) {
        return assets.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Portfolio asset not found: " + id));
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}
