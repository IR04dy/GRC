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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StrategyRoiService} (Slice 9-D).
 *
 * <p>Covers the contract we promise the controller and the parts a
 * Saudi-bank CFO actually relies on:
 * <ul>
 *   <li>ROI calculation happy path — ACTIVE_ACTIVE yields positive ROI</li>
 *   <li>NPV correctness at SAMA 8% discount rate</li>
 *   <li>Payback period truncation — NULL when no payback within horizon</li>
 *   <li>Cold-DR low-cost scenario produces high ROI but RTO matters
 *       (caller's recommendation logic, not service math)</li>
 *   <li>Tenant isolation — every read filters by org</li>
 *   <li>Recommendation state machine: PENDING → APPROVED, PENDING →
 *       DEFERRED → APPROVED, APPROVED requires re-open to edit</li>
 *   <li>F8 event emission on every mutation</li>
 *   <li>Comparison endpoint guards against mixed-BIA comparisons</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StrategyRoiServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private StrategyRoiAnalysisRepository repo;
    @Mock private BiaQuantitativeRepository biaRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private StrategyRoiService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Default discount rate — Spring would inject this via @Value at
        // runtime; under Mockito we set it explicitly so the math is
        // deterministic.
        ReflectionTestUtils.setField(service, "defaultDiscountRate", new BigDecimal("8.00"));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    /** BIA-Q-2026-001 shape with the same band totals as the V167 seed. */
    private static BiaQuantitative biaFixture() {
        BiaQuantitative b = new BiaQuantitative();
        b.setId(501L);
        b.setOrganisationId(ORG_ID);
        b.setAssessmentCode("BIA-Q-2026-001");
        b.setTotalLoss24hSar(new BigDecimal("12950000"));
        b.setTotalLoss72hSar(new BigDecimal("32650000"));
        b.setTotalLoss1wSar(new BigDecimal("71400000"));
        b.setTotalLoss2wSar(new BigDecimal("128900000"));
        b.setTotalLoss1mSar(new BigDecimal("245200000"));
        b.setMaxTolerableLossSar(new BigDecimal("25000000"));
        b.setMtpdBreachAt("72h");
        return b;
    }

    private static StrategyRoiRequest activeActiveReq() {
        return new StrategyRoiRequest(
            "ROI-2026-001",
            501L,                                 // biaQuantitativeId
            null,                                  // strategyId
            "Active-active SARIE feed across Riyadh + Jeddah",
            "بثّ سريع نشط-نشط بين الرياض وجدة",
            "ACTIVE_ACTIVE",
            5,                                     // horizon
            new BigDecimal("8000000"),             // capex
            new BigDecimal("1500000"),             // opex
            1,                                     // RTO 1h → 24h band
            new BigDecimal("0.50"),                // 0.5 incidents/year
            new BigDecimal("8.00")                 // discount rate 8%
        );
    }

    private static StrategyRoiRequest coldDrReq() {
        return new StrategyRoiRequest(
            "ROI-2026-003",
            501L, null,
            "Cold DR — tape restore",
            "تعافٍ بارد — استرداد من شرائط",
            "COLD_DR",
            5,
            new BigDecimal("500000"),               // very low capex
            new BigDecimal("100000"),               // very low opex
            24,                                      // RTO 24h → 24h band
            new BigDecimal("0.50"),
            new BigDecimal("8.00")
        );
    }

    private static StrategyRoiAnalysis existing(String recommendation) {
        StrategyRoiAnalysis a = new StrategyRoiAnalysis();
        a.setId(101L);
        a.setOrganisationId(ORG_ID);
        a.setExternalId(UUID.randomUUID());
        a.setAnalysisCode("ROI-2026-001");
        a.setBiaQuantitativeId(501L);
        a.setStrategyType("ACTIVE_ACTIVE");
        a.setStrategyNameEn("Active-active");
        a.setStrategyNameAr("نشط-نشط");
        a.setPlanningHorizonYears(5);
        a.setCapexSar(new BigDecimal("8000000"));
        a.setOpexAnnualSar(new BigDecimal("1500000"));
        a.setExpectedRtoHours(1);
        a.setExpectedIncidentsPerYear(new BigDecimal("0.50"));
        a.setAvoidableLossPerIncidentSar(new BigDecimal("12950000"));
        a.setTotalAvoidableLossSar(new BigDecimal("32375000"));
        a.setTotalCostSar(new BigDecimal("15500000"));
        a.setRoiPercent(new BigDecimal("108.87"));
        a.setPaybackPeriodMonths(new BigDecimal("14.83"));
        a.setNpvSar(new BigDecimal("11863683"));
        a.setDiscountRatePercent(new BigDecimal("8.00"));
        a.setRecommendation(recommendation);
        return a;
    }

    // ─── ROI calculation happy path ──────────────────────────────────

    @Test void create_activeActive_yieldsPositiveRoi() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(any(), any()))
            .thenReturn(false);
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> {
            StrategyRoiAnalysis a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        StrategyRoiResponse out = service.create(activeActiveReq());

        // RTO 1h → 24h band cumulative = 12.95M
        assertThat(out.avoidableLossPerIncidentSar()).isEqualByComparingTo("12950000");
        // total_avoidable = 12.95M × 0.5 × 5 = 32.375M
        assertThat(out.totalAvoidableLossSar()).isEqualByComparingTo("32375000");
        // total_cost = 8M + (1.5M × 5) = 15.5M
        assertThat(out.totalCostSar()).isEqualByComparingTo("15500000");
        // roi% = (32.375M - 15.5M) / 15.5M × 100 = 108.87
        assertThat(out.roiPercent()).isEqualByComparingTo("108.87");
        // payback months = 8M / (12.95M × 0.5) × 12 = 14.83
        assertThat(out.paybackPeriodMonths()).isEqualByComparingTo("14.83");
        assertThat(out.recommendation()).isEqualTo("PENDING_REVIEW");
    }

    @Test void create_coldDr_yieldsHugeRoiButLongerRto() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(any(), any()))
            .thenReturn(false);
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> {
            StrategyRoiAnalysis a = inv.getArgument(0);
            a.setId(103L);
            return a;
        });

        StrategyRoiResponse out = service.create(coldDrReq());

        // RTO 24h → 24h band still 12.95M
        assertThat(out.avoidableLossPerIncidentSar()).isEqualByComparingTo("12950000");
        assertThat(out.totalAvoidableLossSar()).isEqualByComparingTo("32375000");
        // total_cost = 500K + (100K × 5) = 1M
        assertThat(out.totalCostSar()).isEqualByComparingTo("1000000");
        // roi% = (32.375M - 1M) / 1M × 100 = 3137.50
        assertThat(out.roiPercent()).isEqualByComparingTo("3137.50");
        // payback months = 500K / (12.95M × 0.5) × 12 ≈ 0.93
        assertThat(out.paybackPeriodMonths()).isEqualByComparingTo("0.93");
    }

    /**
     * NPV correctness at 8%: yearly cashflow = avoidable_per_incident × freq − opex
     *                                        = 12.95M × 0.5 − 1.5M = 4.975M
     *   PV factor over 5y at 8% = 3.9927  →  PV ≈ 19,863,683
     *   NPV = PV − capex (8M) ≈ 11,863,683
     */
    @Test void create_activeActive_npvAtEightPercent() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(any(), any()))
            .thenReturn(false);
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        StrategyRoiResponse out = service.create(activeActiveReq());

        // Tolerance: ±SAR 1000 covers HALF_UP rounding through the 5-year sum.
        BigDecimal npv = out.npvSar();
        assertThat(npv).isNotNull();
        assertThat(npv.subtract(new BigDecimal("11863683")).abs())
            .as("NPV at 8%% over 5 yrs (ACTIVE_ACTIVE)")
            .isLessThan(new BigDecimal("1000"));
    }

    /**
     * Payback should be NULL when no payback within the planning horizon.
     * Set capex so high that even all 5 years of avoidable loss cannot
     * recover it.
     */
    @Test void recompute_paybackNullWhenBeyondHorizon() {
        // capex 100M, avoidable 12.95M × 0.5 = 6.475M/yr → payback = 185 months
        // > 5 years (60 months) → service must return NULL.
        StrategyRoiAnalysis a = new StrategyRoiAnalysis();
        a.setOrganisationId(ORG_ID);
        a.setBiaQuantitativeId(501L);
        a.setStrategyType("ACTIVE_ACTIVE");
        a.setPlanningHorizonYears(5);
        a.setCapexSar(new BigDecimal("100000000"));
        a.setOpexAnnualSar(new BigDecimal("1000000"));
        a.setExpectedRtoHours(1);
        a.setExpectedIncidentsPerYear(new BigDecimal("0.50"));
        a.setDiscountRatePercent(new BigDecimal("8.00"));

        service.recompute(a, biaFixture());

        assertThat(a.getPaybackPeriodMonths()).isNull();
        // ROI should be negative — cost dwarfs avoidable loss
        assertThat(a.getRoiPercent()).isNegative();
    }

    @Test void recompute_paybackNullWhenAvoidableIsZero() {
        BiaQuantitative emptyBia = new BiaQuantitative();
        emptyBia.setId(900L);
        // No band totals set → pickAvoidableLossFromBia returns 0
        StrategyRoiAnalysis a = new StrategyRoiAnalysis();
        a.setBiaQuantitativeId(900L);
        a.setStrategyType("MANUAL_WORKAROUND");
        a.setPlanningHorizonYears(5);
        a.setCapexSar(new BigDecimal("100000"));
        a.setOpexAnnualSar(new BigDecimal("10000"));
        a.setExpectedRtoHours(2);
        a.setExpectedIncidentsPerYear(new BigDecimal("0.50"));
        a.setDiscountRatePercent(new BigDecimal("8.00"));

        service.recompute(a, emptyBia);

        assertThat(a.getAvoidableLossPerIncidentSar()).isEqualByComparingTo("0");
        assertThat(a.getPaybackPeriodMonths()).isNull();
    }

    @Test void pickAvoidableLossFromBia_mapsRtoToCorrectBand() {
        BiaQuantitative b = biaFixture();
        assertThat(StrategyRoiService.pickAvoidableLossFromBia(b, 1))
            .isEqualByComparingTo("12950000");      // ≤24h → 24h
        assertThat(StrategyRoiService.pickAvoidableLossFromBia(b, 24))
            .isEqualByComparingTo("12950000");
        assertThat(StrategyRoiService.pickAvoidableLossFromBia(b, 48))
            .isEqualByComparingTo("32650000");      // ≤72h → 72h
        assertThat(StrategyRoiService.pickAvoidableLossFromBia(b, 100))
            .isEqualByComparingTo("71400000");      // ≤168h → 1w
        assertThat(StrategyRoiService.pickAvoidableLossFromBia(b, 300))
            .isEqualByComparingTo("128900000");     // ≤336h → 2w
        assertThat(StrategyRoiService.pickAvoidableLossFromBia(b, 720))
            .isEqualByComparingTo("245200000");     // else → 1m
    }

    // ─── Validation ──────────────────────────────────────────────────

    @Test void create_invalidStrategyType_rejected() {
        StrategyRoiRequest req = new StrategyRoiRequest(
            null, 501L, null,
            "name en", "اسم",
            "BOGUS_TYPE",                          // not in enum
            5, new BigDecimal("1"), new BigDecimal("1"),
            1, new BigDecimal("0.5"), new BigDecimal("8.00"));
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid strategyType");
    }

    @Test void create_duplicateCode_rejected() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(ORG_ID, "ROI-2026-001"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.create(activeActiveReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void create_missingBia_rejected() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(activeActiveReq()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .hasMessageContaining("Quantitative BIA not found");
    }

    @Test void update_blockedOnApproved() {
        StrategyRoiAnalysis a = existing("APPROVED");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.update(a.getExternalId(), activeActiveReq()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVED");
    }

    // ─── Recommendation state machine ────────────────────────────────

    @Test void recommend_pendingToApproved_ok() {
        StrategyRoiAnalysis a = existing("PENDING_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        StrategyRoiResponse out = service.recommend(a.getExternalId(),
            new RecommendationRequest("APPROVED",
                "Approved with dual-site mandate.",
                "اعتُمد مع تكليف بمواقع مزدوجة."));

        assertThat(out.recommendation()).isEqualTo("APPROVED");
        // F2-223 — the decision-maker is the authenticated principal.
        assertThat(out.decidedByName()).contains("Faisal Al-Subaie");
        assertThat(out.decidedAt()).isNotNull();
    }

    @Test void recommend_pendingToDeferredToApproved_chain() {
        StrategyRoiAnalysis a = existing("PENDING_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        // PENDING → DEFERRED
        StrategyRoiResponse out1 = service.recommend(a.getExternalId(),
            new RecommendationRequest("DEFERRED",
                "Awaiting risk committee assessment.",
                "بانتظار تقييم لجنة المخاطر."));
        assertThat(out1.recommendation()).isEqualTo("DEFERRED");

        // DEFERRED → APPROVED
        // (mock returns the same entity instance — state has advanced)
        StrategyRoiResponse out2 = service.recommend(a.getExternalId(),
            new RecommendationRequest("APPROVED",
                "Re-considered after risk-committee briefing.",
                "أُعيد النظر بعد إحاطة لجنة المخاطر."));
        assertThat(out2.recommendation()).isEqualTo("APPROVED");
    }

    @Test void recommend_approvedTerminal_cannotJumpToDeferred() {
        // APPROVED → DEFERRED is not allowed; must re-open to PENDING_REVIEW first.
        StrategyRoiAnalysis a = existing("APPROVED");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.recommend(a.getExternalId(),
            new RecommendationRequest("DEFERRED", null, null)))
            .isInstanceOf(IllegalTransitionException.class);
        verify(repo, never()).save(any());
    }

    @Test void recommend_rejectedReopenable() {
        StrategyRoiAnalysis a = existing("REJECTED");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        StrategyRoiResponse out = service.recommend(a.getExternalId(),
            new RecommendationRequest("PENDING_REVIEW",
                "Re-opening to incorporate revised BIA figures.",
                "إعادة الفتح لإدماج أرقام BIA المُحدَّثة."));

        assertThat(out.recommendation()).isEqualTo("PENDING_REVIEW");
        // re-open clears the decided_* stamps
        assertThat(out.decidedAt()).isNull();
        assertThat(out.decidedByName()).isNull();
    }

    @Test void recommend_unknownTransitionRejected() {
        StrategyRoiAnalysis a = existing("PENDING_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));

        // PENDING → PENDING is a no-op — guard should reject.
        assertThatThrownBy(() -> service.recommend(a.getExternalId(),
            new RecommendationRequest("PENDING_REVIEW", null, null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── F8 event emission ───────────────────────────────────────────

    @Test void create_emitsRoiCreatedEvent() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(any(), any()))
            .thenReturn(false);
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> {
            StrategyRoiAnalysis a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        service.create(activeActiveReq());

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("STRATEGY_ROI_CREATED");
        assertThat(ev.entityType()).isEqualTo("StrategyRoiAnalysis");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("strategyType", "ACTIVE_ACTIVE");
    }

    @Test void recommend_emitsRecommendationEvent() {
        StrategyRoiAnalysis a = existing("PENDING_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recommend(a.getExternalId(),
            new RecommendationRequest("APPROVED",
                "ok.", "حسناً."));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("STRATEGY_ROI_RECOMMENDATION");
        assertThat(ev.payload()).containsEntry("recommendation", "APPROVED");
        assertThat(ev.payload()).containsEntry("previousRecommendation", "PENDING_REVIEW");
    }

    @Test void update_emitsRoiRecomputedEvent() {
        StrategyRoiAnalysis a = existing("PENDING_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, a.getExternalId()))
            .thenReturn(Optional.of(a));
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.save(any(StrategyRoiAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(a.getExternalId(), activeActiveReq());

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("STRATEGY_ROI_RECOMPUTED");
    }

    // ─── Tenant isolation ───────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of());
        service.list();
        verify(repo, times(1)).findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID);
    }

    @Test void listForBia_alwaysFiltersByOrganisation() {
        when(repo.findByOrganisationIdAndBiaQuantitativeIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID, 501L))
            .thenReturn(List.of());
        service.listForBia(501L);
        verify(repo, times(1))
            .findByOrganisationIdAndBiaQuantitativeIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID, 501L);
    }

    @Test void create_setsOrgFromTenantContext() {
        when(biaRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(biaFixture()));
        when(repo.existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(any(), any()))
            .thenReturn(false);
        ArgumentCaptor<StrategyRoiAnalysis> captor = ArgumentCaptor.forClass(StrategyRoiAnalysis.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> {
            StrategyRoiAnalysis a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        service.create(activeActiveReq());

        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    // ─── Comparison endpoint ─────────────────────────────────────────

    @Test void comparison_returnsRowsWhenAllReferenceSameBia() {
        StrategyRoiAnalysis a1 = existing("APPROVED"); a1.setId(1L); a1.setBiaQuantitativeId(501L);
        StrategyRoiAnalysis a2 = existing("PENDING_REVIEW"); a2.setId(2L); a2.setBiaQuantitativeId(501L);

        when(repo.findByOrganisationIdAndIdInAndIsDeletedFalse(eq(ORG_ID), any()))
            .thenReturn(List.of(a1, a2));

        StrategyRoiComparison out = service.comparison(List.of(1L, 2L));
        assertThat(out.biaQuantitativeId()).isEqualTo(501L);
        assertThat(out.analyses()).hasSize(2);
    }

    @Test void comparison_rejectsMixedBiaIds() {
        StrategyRoiAnalysis a1 = existing("APPROVED"); a1.setId(1L); a1.setBiaQuantitativeId(501L);
        StrategyRoiAnalysis a2 = existing("PENDING_REVIEW"); a2.setId(2L); a2.setBiaQuantitativeId(502L);

        when(repo.findByOrganisationIdAndIdInAndIsDeletedFalse(eq(ORG_ID), any()))
            .thenReturn(List.of(a1, a2));

        assertThatThrownBy(() -> service.comparison(List.of(1L, 2L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same BIA");
    }

    @Test void comparison_emptyIdsRejected() {
        assertThatThrownBy(() -> service.comparison(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
