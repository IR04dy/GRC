package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FeLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTargetRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTargetResponse;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.FinancedEmissionsTotal;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TargetProgress;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsRecord;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsTarget;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsRecordRepository;
import com.tss.grc.module.sust.climate.repository.FinancedEmissionsTargetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle + analytics service for PCAF financed-emissions records
 * and net-zero / interim reduction targets — Sprint 11 (V186).
 *
 * <p>Record lifecycle:
 * <pre>
 *   DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
 * </pre>
 *
 * <p>{@code total_financed_emissions_tco2e} is recomputed on every
 * save as {@code (scope1 + scope2 + scope3) × attribution_factor}.
 * Once {@code PUBLISHED} the row is locked — IFRS S2 requires that
 * published figures stay stable; any restatement goes through a new
 * row tagged ARCHIVED + a corrected successor.
 *
 * <p>F8 events emitted:
 * <ul>
 *   <li>{@code FINANCED_EMISSIONS_CREATED}</li>
 *   <li>{@code FINANCED_EMISSIONS_SUBMITTED} (DRAFT → IN_REVIEW)</li>
 *   <li>{@code FINANCED_EMISSIONS_APPROVED} (IN_REVIEW → APPROVED)</li>
 *   <li>{@code FINANCED_EMISSIONS_PUBLISHED} (APPROVED → PUBLISHED)</li>
 *   <li>{@code FINANCED_EMISSIONS_ARCHIVED}</li>
 *   <li>{@code FINANCED_EMISSIONS_TARGET_CREATED}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FinancedEmissionsService {

    private static final Map<String, Set<String>> ALLOWED_NEXT = Map.of(
            "DRAFT",     Set.of("IN_REVIEW"),
            "IN_REVIEW", Set.of("DRAFT", "APPROVED"),
            "APPROVED",  Set.of("PUBLISHED", "IN_REVIEW"),
            "PUBLISHED", Set.of("ARCHIVED"),
            "ARCHIVED",  Set.of()
    );

    private final FinancedEmissionsRecordRepository recordRepo;
    private final FinancedEmissionsTargetRepository targetRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FinancedEmissionsResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return recordRepo.findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(orgId)
                .stream().map(FinancedEmissionsResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FinancedEmissionsResponse> listForYear(int year) {
        Long orgId = TenantContext.getCurrentTenant();
        return recordRepo
                .findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(orgId, year)
                .stream().map(FinancedEmissionsResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FinancedEmissionsResponse get(UUID externalId) {
        return FinancedEmissionsResponse.from(mustGet(externalId));
    }

    @Transactional(readOnly = true)
    public List<FinancedEmissionsTargetResponse> listTargets() {
        Long orgId = TenantContext.getCurrentTenant();
        return targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(orgId)
                .stream().map(FinancedEmissionsTargetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FinancedEmissionsTargetResponse getTarget(UUID externalId) {
        return FinancedEmissionsTargetResponse.from(mustGetTarget(externalId));
    }

    // ─── Record writes ────────────────────────────────────────────

    @Transactional
    public FinancedEmissionsResponse create(FinancedEmissionsRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        FinancedEmissionsRecord r = new FinancedEmissionsRecord();
        r.setOrganisationId(orgId);
        applyFields(r, req);
        r.setLifecycleStage("DRAFT");
        recomputeTotal(r);
        r = recordRepo.save(r);

        audit.log("CREATE", "FinancedEmissionsRecord", r.getId(),
                "Financed emissions " + r.getReportingYear() + "/" + r.getReportingPeriod()
                        + " · class=" + r.getAssetClass()
                        + " · total=" + r.getTotalFinancedEmissionsTco2e() + " tCO2e");
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "FINANCED_EMISSIONS_CREATED",
                "FinancedEmissionsRecord", r.getId(), recordCode(r),
                payload(r, null)));
        return FinancedEmissionsResponse.from(r);
    }

    @Transactional
    public FinancedEmissionsResponse update(UUID externalId, FinancedEmissionsRequest req) {
        FinancedEmissionsRecord r = mustGet(externalId);
        if ("PUBLISHED".equals(r.getLifecycleStage())
                || "ARCHIVED".equals(r.getLifecycleStage())) {
            throw new IllegalTransitionException(r.getLifecycleStage(), "UPDATED",
                    Set.of("DRAFT", "IN_REVIEW", "APPROVED"));
        }
        applyFields(r, req);
        recomputeTotal(r);
        r = recordRepo.save(r);
        audit.log("UPDATE", "FinancedEmissionsRecord", r.getId(),
                "Financed emissions updated · class=" + r.getAssetClass()
                        + " · total=" + r.getTotalFinancedEmissionsTco2e() + " tCO2e");
        return FinancedEmissionsResponse.from(r);
    }

    @Transactional
    public FinancedEmissionsResponse transition(UUID externalId, FeLifecycleTransitionRequest req) {
        FinancedEmissionsRecord r = mustGet(externalId);
        String from = r.getLifecycleStage();
        String to = req.toStage();
        Set<String> allowed = ALLOWED_NEXT.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
        r.setLifecycleStage(to);
        r = recordRepo.save(r);

        String triggerEvent = switch (to) {
            case "IN_REVIEW" -> "DRAFT".equals(from)
                    ? "FINANCED_EMISSIONS_SUBMITTED"
                    : "FINANCED_EMISSIONS_LIFECYCLE_TRANSITION";
            case "APPROVED"  -> "FINANCED_EMISSIONS_APPROVED";
            case "PUBLISHED" -> "FINANCED_EMISSIONS_PUBLISHED";
            case "ARCHIVED"  -> "FINANCED_EMISSIONS_ARCHIVED";
            default          -> "FINANCED_EMISSIONS_LIFECYCLE_TRANSITION";
        };
        audit.log("TRANSITION", "FinancedEmissionsRecord", r.getId(),
                "Financed emissions " + r.getReportingYear() + "/" + r.getAssetClass()
                        + " " + from + " → " + to);
        eventPublisher.publishEvent(new DomainEvent(
                r.getOrganisationId(), triggerEvent,
                "FinancedEmissionsRecord", r.getId(), recordCode(r),
                payload(r, from)));
        return FinancedEmissionsResponse.from(r);
    }

    // ─── Target writes ────────────────────────────────────────────

    @Transactional
    public FinancedEmissionsTargetResponse createTarget(FinancedEmissionsTargetRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        FinancedEmissionsTarget t = new FinancedEmissionsTarget();
        t.setOrganisationId(orgId);
        t.setTargetCode(req.targetCode() == null || req.targetCode().isBlank()
                ? generateTargetCode(orgId, req.targetYear())
                : req.targetCode());
        t.setTitleEn(req.titleEn());
        t.setTitleAr(req.titleAr());
        t.setTargetYear(req.targetYear());
        t.setBaselineYear(req.baselineYear());
        t.setBaselineEmissionsTco2e(req.baselineEmissionsTco2e());
        t.setTargetEmissionsTco2e(req.targetEmissionsTco2e());
        t.setReductionPercent(computeReductionPercent(req.baselineEmissionsTco2e(), req.targetEmissionsTco2e()));
        t.setSbtiAligned(Boolean.TRUE.equals(req.sbtiAligned()));
        t.setAlignmentFramework(req.alignmentFramework());
        t = targetRepo.save(t);

        audit.log("CREATE", "FinancedEmissionsTarget", t.getId(),
                "Target " + t.getTargetCode() + " · " + t.getReductionPercent()
                        + "% reduction by " + t.getTargetYear() + " (vs " + t.getBaselineYear() + ")");
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "FINANCED_EMISSIONS_TARGET_CREATED",
                "FinancedEmissionsTarget", t.getId(), t.getTargetCode(),
                Map.of(
                        "targetCode", safe(t.getTargetCode()),
                        "targetYear", t.getTargetYear() == null ? 0 : t.getTargetYear(),
                        "reductionPercent", t.getReductionPercent() == null ? "" : t.getReductionPercent().toPlainString(),
                        "alignmentFramework", safe(t.getAlignmentFramework())
                )));
        return FinancedEmissionsTargetResponse.from(t);
    }

    // ─── Analytics ────────────────────────────────────────────────

    /**
     * Roll up every {@code APPROVED} or {@code PUBLISHED} record for the
     * given year into a single total. Used by the annual climate report.
     */
    @Transactional(readOnly = true)
    public FinancedEmissionsTotal computeTotalForYear(int year, String period) {
        Long orgId = TenantContext.getCurrentTenant();
        List<FinancedEmissionsRecord> rows = (period == null || period.isBlank())
                ? recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(orgId, year)
                : recordRepo.findByOrganisationIdAndReportingYearAndReportingPeriodAndIsDeletedFalseOrderByAssetClassAsc(orgId, year, period);
        // Only count records that are at least APPROVED for the rolled-up
        // disclosure total — drafts and in-review numbers must not leak.
        List<FinancedEmissionsRecord> material = rows.stream()
                .filter(r -> {
                    String stage = r.getLifecycleStage();
                    return "APPROVED".equals(stage) || "PUBLISHED".equals(stage);
                })
                .toList();
        if (material.isEmpty()) {
            return new FinancedEmissionsTotal(year, period == null ? "ANNUAL" : period,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal s1 = BigDecimal.ZERO, s2 = BigDecimal.ZERO, s3 = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal weightedQuality = BigDecimal.ZERO;
        BigDecimal totalOutstandingForQuality = BigDecimal.ZERO;
        for (FinancedEmissionsRecord r : material) {
            BigDecimal nz1 = nz(r.getEmissionsScope1Tco2e());
            BigDecimal nz2 = nz(r.getEmissionsScope2Tco2e());
            BigDecimal nz3 = nz(r.getEmissionsScope3Tco2e());
            total = total.add(nz(r.getTotalFinancedEmissionsTco2e()));
            // Attribute each scope share to us so analysts see the partition.
            BigDecimal factor = nz(r.getAttributionFactor());
            s1 = s1.add(nz1.multiply(factor));
            s2 = s2.add(nz2.multiply(factor));
            s3 = s3.add(nz3.multiply(factor));
            BigDecimal outAmt = nz(r.getOutstandingAmountSarMillions());
            outstanding = outstanding.add(outAmt);
            weightedQuality = weightedQuality.add(outAmt.multiply(BigDecimal.valueOf(r.getDataQualityScore())));
            totalOutstandingForQuality = totalOutstandingForQuality.add(outAmt);
        }
        BigDecimal avgQuality = totalOutstandingForQuality.signum() == 0
                ? BigDecimal.ZERO
                : weightedQuality.divide(totalOutstandingForQuality, 2, RoundingMode.HALF_UP);
        return new FinancedEmissionsTotal(
                year, period == null ? "ANNUAL" : period,
                total.setScale(2, RoundingMode.HALF_UP),
                s1.setScale(2, RoundingMode.HALF_UP),
                s2.setScale(2, RoundingMode.HALF_UP),
                s3.setScale(2, RoundingMode.HALF_UP),
                outstanding.setScale(2, RoundingMode.HALF_UP),
                avgQuality,
                material.size());
    }

    /**
     * Compare each target's baseline against the latest available roll-up
     * for the org and compute the achieved reduction percent vs the
     * commitment.
     */
    @Transactional(readOnly = true)
    public List<TargetProgress> computeReductionAgainstTarget() {
        Long orgId = TenantContext.getCurrentTenant();
        List<FinancedEmissionsTarget> targets = targetRepo
                .findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(orgId);
        if (targets.isEmpty()) return List.of();

        // Find the most recent reporting year with APPROVED/PUBLISHED rows.
        List<FinancedEmissionsRecord> all = recordRepo
                .findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(orgId);
        Optional<Integer> latestYear = all.stream()
                .filter(r -> "APPROVED".equals(r.getLifecycleStage()) || "PUBLISHED".equals(r.getLifecycleStage()))
                .map(FinancedEmissionsRecord::getReportingYear)
                .max(Comparator.naturalOrder());

        final BigDecimal currentTotal;
        final Integer currentYear;
        if (latestYear.isPresent()) {
            currentYear = latestYear.get();
            FinancedEmissionsTotal tot = computeTotalForYear(currentYear, null);
            currentTotal = tot.totalFinancedEmissionsTco2e();
        } else {
            currentYear = null;
            currentTotal = BigDecimal.ZERO;
        }

        return targets.stream().map(t -> {
            BigDecimal baseline = nz(t.getBaselineEmissionsTco2e());
            BigDecimal targetAbs = nz(t.getTargetEmissionsTco2e());
            BigDecimal target = nz(t.getReductionPercent());
            BigDecimal achievedReduction;
            BigDecimal achievementRatio;
            if (baseline.signum() == 0) {
                achievedReduction = BigDecimal.ZERO;
                achievementRatio = BigDecimal.ZERO;
            } else {
                achievedReduction = baseline.subtract(currentTotal)
                        .divide(baseline, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                achievementRatio = target.signum() == 0
                        ? BigDecimal.ZERO
                        : achievedReduction.divide(target, 4, RoundingMode.HALF_UP);
            }
            return new TargetProgress(
                    t.getExternalId(), t.getTargetCode(),
                    t.getTitleEn(), t.getTitleAr(),
                    t.getBaselineYear(), baseline,
                    t.getTargetYear(), targetAbs,
                    target,
                    currentTotal,
                    currentYear,
                    achievedReduction,
                    achievementRatio,
                    t.getSbtiAligned(), t.getAlignmentFramework());
        }).toList();
    }

    /**
     * Static helper — exposed so the controller test layer can verify the
     * reduction-percent math directly without spinning the full service
     * lifecycle.
     */
    public static BigDecimal computeReductionPercent(BigDecimal baseline, BigDecimal target) {
        BigDecimal b = baseline == null ? BigDecimal.ZERO : baseline;
        BigDecimal t = target == null ? BigDecimal.ZERO : target;
        if (b.signum() == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return b.subtract(t)
                .divide(b, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    public FinancedEmissionsRecord mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("FinancedEmissionsRecord not found: " + externalId));
    }

    public FinancedEmissionsTarget mustGetTarget(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return targetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("FinancedEmissionsTarget not found: " + externalId));
    }

    private void applyFields(FinancedEmissionsRecord r, FinancedEmissionsRequest req) {
        if (req.reportingYear() != null) r.setReportingYear(req.reportingYear());
        if (req.reportingPeriod() != null) r.setReportingPeriod(req.reportingPeriod());
        if (req.assetClass() != null) r.setAssetClass(req.assetClass());
        if (req.assetClassLabelEn() != null) r.setAssetClassLabelEn(req.assetClassLabelEn());
        if (req.assetClassLabelAr() != null) r.setAssetClassLabelAr(req.assetClassLabelAr());
        if (req.outstandingAmountSarMillions() != null) r.setOutstandingAmountSarMillions(req.outstandingAmountSarMillions());
        if (req.attributionFactor() != null) r.setAttributionFactor(req.attributionFactor());
        if (req.dataQualityScore() != null) r.setDataQualityScore(req.dataQualityScore());
        if (req.emissionsScope1Tco2e() != null) r.setEmissionsScope1Tco2e(req.emissionsScope1Tco2e());
        if (req.emissionsScope2Tco2e() != null) r.setEmissionsScope2Tco2e(req.emissionsScope2Tco2e());
        if (req.emissionsScope3Tco2e() != null) r.setEmissionsScope3Tco2e(req.emissionsScope3Tco2e());
        if (req.methodologyEn() != null) r.setMethodologyEn(req.methodologyEn());
        if (req.methodologyAr() != null) r.setMethodologyAr(req.methodologyAr());
    }

    private static void recomputeTotal(FinancedEmissionsRecord r) {
        BigDecimal s1 = nz(r.getEmissionsScope1Tco2e());
        BigDecimal s2 = nz(r.getEmissionsScope2Tco2e());
        BigDecimal s3 = nz(r.getEmissionsScope3Tco2e());
        BigDecimal factor = nz(r.getAttributionFactor());
        BigDecimal total = s1.add(s2).add(s3).multiply(factor);
        r.setTotalFinancedEmissionsTco2e(total.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private String generateTargetCode(Long orgId, Integer year) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format(Locale.ROOT, "FE-TARGET-%d-%03d", year == null ? 0 : year, seq);
            if (targetRepo.countByOrganisationIdAndTargetCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return "FE-TARGET-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static String recordCode(FinancedEmissionsRecord r) {
        return r.getReportingYear() + "/" + safe(r.getReportingPeriod()) + "/" + safe(r.getAssetClass());
    }

    private static Map<String, Object> payload(FinancedEmissionsRecord r, String previousStage) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("reportingYear",   r.getReportingYear() == null ? 0 : r.getReportingYear());
        p.put("reportingPeriod", safe(r.getReportingPeriod()));
        p.put("assetClass",      safe(r.getAssetClass()));
        p.put("externalId",      r.getExternalId() == null ? "" : r.getExternalId().toString());
        p.put("totalFinancedEmissionsTco2e",
                r.getTotalFinancedEmissionsTco2e() == null ? "" : r.getTotalFinancedEmissionsTco2e().toPlainString());
        p.put("dataQualityScore", r.getDataQualityScore() == null ? 0 : r.getDataQualityScore());
        p.put("lifecycleStage",   safe(r.getLifecycleStage()));
        if (previousStage != null) p.put("previousStage", previousStage);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
