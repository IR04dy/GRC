package com.tss.grc.module.bcm.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.RecommendationRequest;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.StrategyRoiComparison;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.StrategyRoiRequest;
import com.tss.grc.module.bcm.dto.StrategyRoiDtos.StrategyRoiResponse;
import com.tss.grc.module.bcm.entity.BiaQuantitative;
import com.tss.grc.module.bcm.entity.StrategyRoiAnalysis;
import com.tss.grc.module.bcm.repository.BiaQuantitativeRepository;
import com.tss.grc.module.bcm.repository.StrategyRoiAnalysisRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Slice 9-D — Continuity Strategy ROI service.
 *
 * <p>Computes the board-ready ROI table that justifies every proposed
 * continuity investment. Reads avoidable-loss values from a
 * {@link BiaQuantitative} parent (Slice 9-C) and produces:
 *
 * <ul>
 *   <li><b>avoidable_loss_per_incident</b> — read from the BIA at the
 *       band matching {@code expectedRtoHours} (≤1h..24h → 24h band;
 *       ≤72h → 72h band; ≤168h → 1w band; ≤336h → 2w band; else 1m).</li>
 *   <li><b>total_avoidable_loss</b> = avoidable × frequency × horizon.</li>
 *   <li><b>total_cost</b> = capex + (opex × horizon).</li>
 *   <li><b>roi%</b> = ((avoidable - cost) / cost) × 100.</li>
 *   <li><b>NPV</b> = Σ (annual_net_cashflow / (1+r)^t) − capex, where
 *       annual_net_cashflow = (avoidable × frequency) − opex_annual.</li>
 *   <li><b>payback months</b> = capex / (avoidable × frequency) × 12,
 *       or {@code null} if no payback within {@code planningHorizonYears}.</li>
 * </ul>
 *
 * <h3>Recommendation state machine</h3>
 * <pre>
 *   PENDING_REVIEW → APPROVED | REJECTED | DEFERRED
 *   DEFERRED       → APPROVED | REJECTED | PENDING_REVIEW
 *   APPROVED       (terminal until reopened to PENDING_REVIEW)
 *   REJECTED       (terminal until reopened to PENDING_REVIEW)
 * </pre>
 *
 * <p>Every mutation writes an F7 audit row and emits an F8 domain
 * event ({@code STRATEGY_ROI_CREATED}, {@code STRATEGY_ROI_RECOMPUTED},
 * {@code STRATEGY_ROI_RECOMMENDATION}).
 */
@Service
@RequiredArgsConstructor
public class StrategyRoiService {

    private static final Logger log = LoggerFactory.getLogger(StrategyRoiService.class);

    public static final Set<String> STRATEGY_TYPES = Set.of(
        "ACTIVE_ACTIVE", "WARM_DR", "COLD_DR", "VENDOR_FAILOVER",
        "MANUAL_WORKAROUND", "INSURANCE_TRANSFER");

    public static final Set<String> RECOMMENDATIONS = Set.of(
        "PENDING_REVIEW", "APPROVED", "REJECTED", "DEFERRED");

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE  = 2;

    private final StrategyRoiAnalysisRepository repo;
    private final BiaQuantitativeRepository biaRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** SAMA reference rate (default 8%). Override with {@code grc.bcm.roi.discount-rate}. */
    @Value("${grc.bcm.roi.discount-rate:8.00}")
    private BigDecimal defaultDiscountRate;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StrategyRoiResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org())
            .stream().map(StrategyRoiService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StrategyRoiResponse> listForBia(Long biaQuantitativeId) {
        return repo.findByOrganisationIdAndBiaQuantitativeIdAndIsDeletedFalseOrderByCreatedAtDesc(
                org(), biaQuantitativeId)
            .stream().map(StrategyRoiService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StrategyRoiResponse get(UUID externalId) {
        return toResponse(loadOrThrow(externalId));
    }

    @Transactional(readOnly = true)
    public StrategyRoiComparison comparison(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("at least one analysis id is required for comparison");
        }
        List<StrategyRoiAnalysis> rows = repo.findByOrganisationIdAndIdInAndIsDeletedFalse(org(), ids);
        if (rows.isEmpty()) {
            throw new EntityNotFoundException("no ROI analyses found for ids: " + ids);
        }
        // Verify all reference the same BIA — comparison only makes sense like-for-like.
        Long biaId = rows.get(0).getBiaQuantitativeId();
        for (StrategyRoiAnalysis r : rows) {
            if (!Objects.equals(r.getBiaQuantitativeId(), biaId)) {
                throw new IllegalArgumentException(
                    "all analyses in a comparison must reference the same BIA "
                        + "(found " + biaId + " and " + r.getBiaQuantitativeId() + ")");
            }
        }
        List<StrategyRoiResponse> responses = rows.stream()
            .map(StrategyRoiService::toResponse).toList();
        return new StrategyRoiComparison(biaId, responses);
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public StrategyRoiResponse create(StrategyRoiRequest req) {
        validateRequest(req);
        Long orgId = org();

        BiaQuantitative bia = biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.biaQuantitativeId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Quantitative BIA not found in this tenant: " + req.biaQuantitativeId()));

        StrategyRoiAnalysis a = new StrategyRoiAnalysis();
        a.setOrganisationId(orgId);
        applyRequest(a, req);
        a.setAnalysisCode(req.analysisCode() != null && !req.analysisCode().isBlank()
            ? req.analysisCode()
            : nextAnalysisCode());
        if (repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(orgId, a.getAnalysisCode())) {
            throw new IllegalArgumentException(
                "ROI analysis code already exists: " + a.getAnalysisCode());
        }
        a.setRecommendation("PENDING_REVIEW");

        recompute(a, bia);

        StrategyRoiAnalysis saved = repo.save(a);
        audit.log("CREATE", "StrategyRoiAnalysis", saved.getId(),
            "Continuity strategy ROI created: " + saved.getAnalysisCode()
                + " (" + saved.getStrategyType() + ", BIA " + bia.getAssessmentCode()
                + ", ROI " + saved.getRoiPercent() + "%)");
        emit("STRATEGY_ROI_CREATED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public StrategyRoiResponse update(UUID externalId, StrategyRoiRequest req) {
        StrategyRoiAnalysis a = loadOrThrow(externalId);
        if ("APPROVED".equals(a.getRecommendation())) {
            throw new IllegalStateException(
                "Cannot edit an APPROVED ROI analysis — re-open via recommendation transition first");
        }
        validateRequest(req);

        BiaQuantitative bia = biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), req.biaQuantitativeId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Quantitative BIA not found in this tenant: " + req.biaQuantitativeId()));

        applyRequest(a, req);
        recompute(a, bia);
        a.setUpdatedAt(LocalDateTime.now());

        StrategyRoiAnalysis saved = repo.save(a);
        audit.log("UPDATE", "StrategyRoiAnalysis", saved.getId(),
            "Continuity strategy ROI updated: " + saved.getAnalysisCode()
                + " (ROI now " + saved.getRoiPercent() + "%)");
        emit("STRATEGY_ROI_RECOMPUTED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public StrategyRoiResponse recommend(UUID externalId, RecommendationRequest req) {
        StrategyRoiAnalysis a = loadOrThrow(externalId);
        String to = req.recommendation();
        if (!RECOMMENDATIONS.contains(to)) {
            throw new IllegalArgumentException("Invalid recommendation: " + to);
        }
        String from = a.getRecommendation();
        guardRecommendation(from, to);

        a.setRecommendation(to);
        a.setRecommendationRationaleEn(req.recommendationRationaleEn());
        a.setRecommendationRationaleAr(req.recommendationRationaleAr());
        if (!"PENDING_REVIEW".equals(to)) {
            a.setDecidedByName(req.decidedByName());
            a.setDecidedByUserId(req.decidedByUserId());
            a.setDecidedAt(LocalDateTime.now());
        } else {
            // Re-opening — clear the decision stamp.
            a.setDecidedByName(null);
            a.setDecidedByUserId(null);
            a.setDecidedAt(null);
        }
        a.setUpdatedAt(LocalDateTime.now());

        StrategyRoiAnalysis saved = repo.save(a);
        audit.log("TRANSITION", "StrategyRoiAnalysis", saved.getId(),
            "ROI recommendation " + saved.getAnalysisCode() + ": " + from + " → " + to
                + (req.decidedByName() != null ? " (decided by " + req.decidedByName() + ")" : ""));
        emit("STRATEGY_ROI_RECOMMENDATION", saved, from);
        return toResponse(saved);
    }

    @Transactional
    public StrategyRoiResponse archive(UUID externalId) {
        StrategyRoiAnalysis a = loadOrThrow(externalId);
        String from = a.getRecommendation();
        a.setIsDeleted(true);
        a.setDeletedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        StrategyRoiAnalysis saved = repo.save(a);
        audit.log("ARCHIVE", "StrategyRoiAnalysis", saved.getId(),
            "ROI analysis archived: " + saved.getAnalysisCode() + " (was " + from + ")");
        emit("STRATEGY_ROI_ARCHIVED", saved, from);
        return toResponse(saved);
    }

    // ─── Calculation core (package-private for unit tests) ──────────

    /**
     * Picks the BIA loss band that matches the strategy's expected RTO.
     *
     * <p>An incident contained within a band is assumed to incur that
     * band's cumulative loss. Lower RTO → smaller band → less avoidable.
     * The {@code 24h} band is used for anything ≤ 24h, the {@code 72h}
     * band for 24h..72h, and so on.
     */
    static BigDecimal pickAvoidableLossFromBia(BiaQuantitative bia, int rtoHours) {
        BigDecimal v;
        if (rtoHours <= 24) {
            v = firstNonNull(bia.getTotalLoss24hSar(),
                             bia.getTotalLoss72hSar(),
                             bia.getTotalLoss1wSar(),
                             bia.getTotalLoss2wSar(),
                             bia.getTotalLoss1mSar());
        } else if (rtoHours <= 72) {
            v = firstNonNull(bia.getTotalLoss72hSar(),
                             bia.getTotalLoss1wSar(),
                             bia.getTotalLoss2wSar(),
                             bia.getTotalLoss1mSar(),
                             bia.getTotalLoss24hSar());
        } else if (rtoHours <= 168) {
            v = firstNonNull(bia.getTotalLoss1wSar(),
                             bia.getTotalLoss2wSar(),
                             bia.getTotalLoss1mSar(),
                             bia.getTotalLoss72hSar(),
                             bia.getTotalLoss24hSar());
        } else if (rtoHours <= 336) {
            v = firstNonNull(bia.getTotalLoss2wSar(),
                             bia.getTotalLoss1mSar(),
                             bia.getTotalLoss1wSar(),
                             bia.getTotalLoss72hSar(),
                             bia.getTotalLoss24hSar());
        } else {
            v = firstNonNull(bia.getTotalLoss1mSar(),
                             bia.getTotalLoss2wSar(),
                             bia.getTotalLoss1wSar(),
                             bia.getTotalLoss72hSar(),
                             bia.getTotalLoss24hSar());
        }
        return v != null ? v : BigDecimal.ZERO;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    /**
     * Single entry-point for the ROI/NPV/payback math. Pure function over
     * the input fields already stored on {@code a} — exposed package-private
     * so the unit test can drive it directly.
     */
    void recompute(StrategyRoiAnalysis a, BiaQuantitative bia) {
        if (a.getDiscountRatePercent() == null) {
            a.setDiscountRatePercent(defaultDiscountRate);
        }

        BigDecimal avoidablePerIncident = pickAvoidableLossFromBia(bia, a.getExpectedRtoHours());
        a.setAvoidableLossPerIncidentSar(scale(avoidablePerIncident));

        BigDecimal frequency = a.getExpectedIncidentsPerYear() != null
            ? a.getExpectedIncidentsPerYear()
            : new BigDecimal("0.50");
        int horizon = a.getPlanningHorizonYears() != null && a.getPlanningHorizonYears() > 0
            ? a.getPlanningHorizonYears() : 5;

        // total_avoidable = per_incident × frequency × horizon
        BigDecimal totalAvoidable = avoidablePerIncident
            .multiply(frequency)
            .multiply(new BigDecimal(horizon));
        a.setTotalAvoidableLossSar(scale(totalAvoidable));

        // total_cost = capex + (opex × horizon)
        BigDecimal capex = nz(a.getCapexSar());
        BigDecimal opex  = nz(a.getOpexAnnualSar());
        BigDecimal totalCost = capex.add(opex.multiply(new BigDecimal(horizon)));
        a.setTotalCostSar(scale(totalCost));

        // roi% = ((avoidable - cost) / cost) × 100
        // Guard divide-by-zero: when total_cost = 0 the strategy is "free"
        // and ROI is undefined; we record it as 0 so the row is still valid.
        if (totalCost.signum() == 0) {
            a.setRoiPercent(BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP));
        } else {
            BigDecimal roi = totalAvoidable.subtract(totalCost)
                .divide(totalCost, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            a.setRoiPercent(roi.setScale(RATE_SCALE, RoundingMode.HALF_UP));
        }

        // payback months = capex / (avoidable × frequency) × 12
        // NULL when no payback within horizon (annual_recovery == 0 OR
        // payback_months > horizon × 12).
        BigDecimal annualRecovery = avoidablePerIncident.multiply(frequency);
        if (annualRecovery.signum() == 0) {
            a.setPaybackPeriodMonths(null);
        } else {
            BigDecimal payback = capex
                .divide(annualRecovery, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("12"))
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal horizonMonths = new BigDecimal(horizon * 12);
            a.setPaybackPeriodMonths(
                payback.compareTo(horizonMonths) > 0 ? null : payback);
        }

        // NPV — discount annual net cashflows over horizon at rate r.
        //   annual_net_cashflow = (avoidable × frequency) − opex_annual
        //   NPV = Σ_t=1..H (cf / (1+r)^t)  −  capex
        BigDecimal rate = a.getDiscountRatePercent()
            .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        BigDecimal cashFlow = annualRecovery.subtract(opex);
        BigDecimal pvSum = BigDecimal.ZERO;
        BigDecimal onePlusR = BigDecimal.ONE.add(rate);
        BigDecimal df = BigDecimal.ONE;
        for (int t = 1; t <= horizon; t++) {
            df = df.multiply(onePlusR);                              // (1+r)^t
            pvSum = pvSum.add(cashFlow.divide(df, 6, RoundingMode.HALF_UP));
        }
        BigDecimal npv = pvSum.subtract(capex);
        a.setNpvSar(scale(npv));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ─── Internal helpers ───────────────────────────────────────────

    private StrategyRoiAnalysis loadOrThrow(UUID externalId) {
        return repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(org(), externalId)
            .orElseThrow(() -> new EntityNotFoundException(
                "ROI analysis not found: " + externalId));
    }

    /**
     * Transition guard for the recommendation state machine.
     *
     * <pre>
     *   PENDING_REVIEW → APPROVED | REJECTED | DEFERRED
     *   DEFERRED       → APPROVED | REJECTED | PENDING_REVIEW
     *   APPROVED       → PENDING_REVIEW (re-open only)
     *   REJECTED       → PENDING_REVIEW (re-open only)
     * </pre>
     */
    private static void guardRecommendation(String from, String to) {
        boolean ok = switch (Objects.requireNonNullElse(from, "")) {
            case "PENDING_REVIEW" -> Set.of("APPROVED", "REJECTED", "DEFERRED").contains(to);
            case "DEFERRED"       -> Set.of("APPROVED", "REJECTED", "PENDING_REVIEW").contains(to);
            case "APPROVED"       -> "PENDING_REVIEW".equals(to);
            case "REJECTED"       -> "PENDING_REVIEW".equals(to);
            default                -> false;
        };
        if (!ok) {
            throw new IllegalTransitionException(from, to, allowedFrom(from));
        }
    }

    private static Set<String> allowedFrom(String from) {
        return switch (Objects.requireNonNullElse(from, "")) {
            case "PENDING_REVIEW" -> Set.of("APPROVED", "REJECTED", "DEFERRED");
            case "DEFERRED"       -> Set.of("APPROVED", "REJECTED", "PENDING_REVIEW");
            case "APPROVED"       -> Set.of("PENDING_REVIEW");
            case "REJECTED"       -> Set.of("PENDING_REVIEW");
            default                -> Set.of();
        };
    }

    private static void validateRequest(StrategyRoiRequest req) {
        if (req.biaQuantitativeId() == null) {
            throw new IllegalArgumentException("biaQuantitativeId is required");
        }
        if (req.strategyType() == null || !STRATEGY_TYPES.contains(req.strategyType())) {
            throw new IllegalArgumentException(
                "Invalid strategyType: " + req.strategyType() + " (allowed: " + STRATEGY_TYPES + ")");
        }
        if (req.expectedRtoHours() == null || req.expectedRtoHours() < 1) {
            throw new IllegalArgumentException("expectedRtoHours must be at least 1");
        }
        if (req.planningHorizonYears() == null || req.planningHorizonYears() < 1) {
            throw new IllegalArgumentException("planningHorizonYears must be at least 1");
        }
        if (req.capexSar() == null || req.capexSar().signum() < 0) {
            throw new IllegalArgumentException("capexSar must be non-negative");
        }
        if (req.opexAnnualSar() == null || req.opexAnnualSar().signum() < 0) {
            throw new IllegalArgumentException("opexAnnualSar must be non-negative");
        }
    }

    private static void applyRequest(StrategyRoiAnalysis a, StrategyRoiRequest req) {
        a.setBiaQuantitativeId(req.biaQuantitativeId());
        a.setStrategyId(req.strategyId());
        a.setStrategyNameEn(req.strategyNameEn());
        a.setStrategyNameAr(req.strategyNameAr());
        a.setStrategyType(req.strategyType());
        a.setPlanningHorizonYears(req.planningHorizonYears());
        a.setCapexSar(req.capexSar());
        a.setOpexAnnualSar(req.opexAnnualSar());
        a.setExpectedRtoHours(req.expectedRtoHours());
        if (req.expectedIncidentsPerYear() != null) {
            a.setExpectedIncidentsPerYear(req.expectedIncidentsPerYear());
        } else if (a.getExpectedIncidentsPerYear() == null) {
            a.setExpectedIncidentsPerYear(new BigDecimal("0.50"));
        }
        if (req.discountRatePercent() != null) {
            a.setDiscountRatePercent(req.discountRatePercent());
        }
    }

    private static String nextAnalysisCode() {
        int year = Year.now().getValue();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "ROI-%04d-%s", year, suffix);
    }

    // ─── F8 emission ────────────────────────────────────────────────

    private void emit(String trigger, StrategyRoiAnalysis a, String previousRecommendation) {
        publisher.publishEvent(new DomainEvent(
            a.getOrganisationId(), trigger, "StrategyRoiAnalysis", a.getId(),
            "strategy_roi:" + a.getId(), payload(a, previousRecommendation)));
    }

    private static Map<String, Object> payload(StrategyRoiAnalysis a, String previousRecommendation) {
        Map<String, Object> m = new HashMap<>();
        m.put("strategyRoiId",        str(a.getId()));
        m.put("analysisCode",         str(a.getAnalysisCode()));
        m.put("biaQuantitativeId",    str(a.getBiaQuantitativeId()));
        m.put("strategyType",         str(a.getStrategyType()));
        m.put("expectedRtoHours",     str(a.getExpectedRtoHours()));
        m.put("totalAvoidableLossSar", str(a.getTotalAvoidableLossSar()));
        m.put("totalCostSar",         str(a.getTotalCostSar()));
        m.put("roiPercent",           str(a.getRoiPercent()));
        m.put("npvSar",               str(a.getNpvSar()));
        m.put("paybackPeriodMonths",  str(a.getPaybackPeriodMonths()));
        m.put("recommendation",       str(a.getRecommendation()));
        m.put("previousRecommendation", str(previousRecommendation));
        return m;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    // ─── Mapping ────────────────────────────────────────────────────

    private static StrategyRoiResponse toResponse(StrategyRoiAnalysis a) {
        return new StrategyRoiResponse(
            a.getId(),
            a.getExternalId(),
            a.getOrganisationId(),
            a.getAnalysisCode(),
            a.getBiaQuantitativeId(),
            a.getStrategyId(),
            a.getStrategyNameEn(),
            a.getStrategyNameAr(),
            a.getStrategyType(),
            a.getPlanningHorizonYears(),
            a.getCapexSar(),
            a.getOpexAnnualSar(),
            a.getExpectedRtoHours(),
            a.getExpectedIncidentsPerYear(),
            a.getAvoidableLossPerIncidentSar(),
            a.getTotalAvoidableLossSar(),
            a.getTotalCostSar(),
            a.getRoiPercent(),
            a.getPaybackPeriodMonths(),
            a.getNpvSar(),
            a.getDiscountRatePercent(),
            a.getRecommendation(),
            a.getRecommendationRationaleEn(),
            a.getRecommendationRationaleAr(),
            a.getDecidedByUserId(),
            a.getDecidedByName(),
            a.getDecidedAt(),
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }

    @SuppressWarnings("unused")
    private Optional<StrategyRoiAnalysis> _touch() { log.trace("bound"); return Optional.empty(); }
}
