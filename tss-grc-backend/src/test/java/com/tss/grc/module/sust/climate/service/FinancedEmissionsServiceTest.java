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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FinancedEmissionsService — Sprint 11 (V186) test backlog.
 *
 * Locks the PCAF-aligned total computation, target reduction-percent
 * math, asset-class roll-up, lifecycle gates (DRAFT → IN_REVIEW →
 * APPROVED → PUBLISHED → ARCHIVED), tenant isolation and F8 events.
 */
@ExtendWith(MockitoExtension.class)
class FinancedEmissionsServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final UUID REC_EX = UUID.randomUUID();
    private static final Long REC_ID = 42L;

    @Mock private FinancedEmissionsRecordRepository recordRepo;
    @Mock private FinancedEmissionsTargetRepository targetRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private FinancedEmissionsService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static FinancedEmissionsRecord rec(String stage, String assetClass,
                                               BigDecimal outstanding, BigDecimal factor,
                                               BigDecimal s1, BigDecimal s2, BigDecimal s3) {
        FinancedEmissionsRecord r = new FinancedEmissionsRecord();
        r.setId(REC_ID);
        r.setExternalId(REC_EX);
        r.setOrganisationId(ORG_ID);
        r.setReportingYear(2026);
        r.setReportingPeriod("ANNUAL");
        r.setAssetClass(assetClass);
        r.setAssetClassLabelEn(assetClass);
        r.setAssetClassLabelAr(assetClass);
        r.setOutstandingAmountSarMillions(outstanding);
        r.setAttributionFactor(factor);
        r.setDataQualityScore(2);
        r.setEmissionsScope1Tco2e(s1);
        r.setEmissionsScope2Tco2e(s2);
        r.setEmissionsScope3Tco2e(s3);
        BigDecimal total = s1.add(s2).add(s3).multiply(factor);
        r.setTotalFinancedEmissionsTco2e(total);
        r.setLifecycleStage(stage);
        return r;
    }

    private static FinancedEmissionsRequest req() {
        return new FinancedEmissionsRequest(
                2026, "ANNUAL", "BUSINESS_LOANS",
                "Business loans", "قروض الأعمال",
                new BigDecimal("58900.00"), new BigDecimal("0.082000"),
                3,
                new BigDecimal("1850000.00"),
                new BigDecimal("420000.00"),
                new BigDecimal("2350000.00"),
                "PCAF methodology", "منهجية PCAF"
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Create + total computation ───────────────────────────────

    @Test
    void create_computesTotalAndStampsDraft() {
        AtomicLong seq = new AtomicLong(100);
        when(recordRepo.save(any(FinancedEmissionsRecord.class))).thenAnswer(assignId(seq));

        FinancedEmissionsResponse out = service.create(req());

        assertThat(out.lifecycleStage()).isEqualTo("DRAFT");
        // (1_850_000 + 420_000 + 2_350_000) * 0.082000 = 4_620_000 * 0.082 = 378840.00
        assertThat(out.totalFinancedEmissionsTco2e())
                .isEqualByComparingTo(new BigDecimal("378840.00"));

        ArgumentCaptor<FinancedEmissionsRecord> cap = ArgumentCaptor.forClass(FinancedEmissionsRecord.class);
        verify(recordRepo).save(cap.capture());
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        verify(audit).log(eq("CREATE"), eq("FinancedEmissionsRecord"), anyLong(), anyString());
    }

    @Test
    void create_emitsCreatedEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(recordRepo.save(any(FinancedEmissionsRecord.class))).thenAnswer(assignId(seq));

        service.create(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("FINANCED_EMISSIONS_CREATED");
        assertThat(cap.getValue().payload()).containsEntry("assetClass", "BUSINESS_LOANS");
        assertThat(cap.getValue().payload()).containsEntry("reportingYear", 2026);
    }

    // ─── Update ───────────────────────────────────────────────────

    @Test
    void update_rejectsOnPublished() {
        FinancedEmissionsRecord r = rec("PUBLISHED", "BUSINESS_LOANS",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("200"));
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.update(REC_EX, req()))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void update_recomputesTotal() {
        FinancedEmissionsRecord r = rec("DRAFT", "BUSINESS_LOANS",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.of(r));
        when(recordRepo.save(any(FinancedEmissionsRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancedEmissionsResponse out = service.update(REC_EX, req());

        // Now the request has scope-1/2/3 numbers — total recomputed.
        assertThat(out.totalFinancedEmissionsTco2e())
                .isEqualByComparingTo(new BigDecimal("378840.00"));
    }

    // ─── Lifecycle transitions ────────────────────────────────────

    @Test
    void transition_draftToInReview_emitsSubmitted() {
        FinancedEmissionsRecord r = rec("DRAFT", "BUSINESS_LOANS",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("200"));
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.of(r));
        when(recordRepo.save(any(FinancedEmissionsRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancedEmissionsResponse out = service.transition(REC_EX,
                new FeLifecycleTransitionRequest("IN_REVIEW"));

        assertThat(out.lifecycleStage()).isEqualTo("IN_REVIEW");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("FINANCED_EMISSIONS_SUBMITTED");
    }

    @Test
    void transition_inReviewToApproved_emitsApproved() {
        FinancedEmissionsRecord r = rec("IN_REVIEW", "BUSINESS_LOANS",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("200"));
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.of(r));
        when(recordRepo.save(any(FinancedEmissionsRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancedEmissionsResponse out = service.transition(REC_EX,
                new FeLifecycleTransitionRequest("APPROVED"));

        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("FINANCED_EMISSIONS_APPROVED");
    }

    @Test
    void transition_approvedToPublished_emitsPublished() {
        FinancedEmissionsRecord r = rec("APPROVED", "BUSINESS_LOANS",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("200"));
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.of(r));
        when(recordRepo.save(any(FinancedEmissionsRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancedEmissionsResponse out = service.transition(REC_EX,
                new FeLifecycleTransitionRequest("PUBLISHED"));

        assertThat(out.lifecycleStage()).isEqualTo("PUBLISHED");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("FINANCED_EMISSIONS_PUBLISHED");
    }

    @Test
    void transition_illegalJumpRejected() {
        FinancedEmissionsRecord r = rec("DRAFT", "BUSINESS_LOANS",
                new BigDecimal("58900"), new BigDecimal("0.082"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("200"));
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.of(r));

        // DRAFT → PUBLISHED is not allowed; must traverse review + approval.
        assertThatThrownBy(() ->
                service.transition(REC_EX, new FeLifecycleTransitionRequest("PUBLISHED")))
                .isInstanceOf(IllegalTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ─── Total + roll-up math ─────────────────────────────────────

    @Test
    void computeTotalForYear_sumsApprovedAndPublishedRecordsOnly() {
        FinancedEmissionsRecord publishedA = rec("PUBLISHED", "BUSINESS_LOANS",
                new BigDecimal("100"), new BigDecimal("0.500"),
                new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("300"));
        // (200+100+300)*0.500 = 300
        FinancedEmissionsRecord approvedB = rec("APPROVED", "MORTGAGES",
                new BigDecimal("400"), new BigDecimal("0.250"),
                BigDecimal.ZERO, new BigDecimal("800"), BigDecimal.ZERO);
        approvedB.setId(43L); approvedB.setExternalId(UUID.randomUUID());
        // (0+800+0)*0.250 = 200
        FinancedEmissionsRecord draftC = rec("DRAFT", "SOVEREIGN_DEBT",
                new BigDecimal("9999"), new BigDecimal("0.999"),
                new BigDecimal("9999"), BigDecimal.ZERO, BigDecimal.ZERO);
        draftC.setId(44L); draftC.setExternalId(UUID.randomUUID());

        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(ORG_ID, 2026))
                .thenReturn(List.of(publishedA, approvedB, draftC));

        FinancedEmissionsTotal total = service.computeTotalForYear(2026, null);

        // 300 + 200 = 500 — draftC is excluded.
        assertThat(total.totalFinancedEmissionsTco2e()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(total.assetClassCount()).isEqualTo(2);
        // Total outstanding = 100 + 400 = 500
        assertThat(total.totalOutstandingSarMillions()).isEqualByComparingTo(new BigDecimal("500.00"));
        // Attributed scope-2 = 100*0.5 + 800*0.25 = 50 + 200 = 250
        assertThat(total.totalScope2Tco2e()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void computeTotalForYear_emptyWhenNothingApproved() {
        FinancedEmissionsRecord draft = rec("DRAFT", "BUSINESS_LOANS",
                new BigDecimal("100"), new BigDecimal("0.5"),
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(ORG_ID, 2026))
                .thenReturn(List.of(draft));

        FinancedEmissionsTotal total = service.computeTotalForYear(2026, null);
        assertThat(total.totalFinancedEmissionsTco2e()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(total.assetClassCount()).isZero();
    }

    // ─── Target math ─────────────────────────────────────────────

    @Test
    void computeReductionPercent_halfReduction() {
        BigDecimal pct = FinancedEmissionsService.computeReductionPercent(
                new BigDecimal("1000"), new BigDecimal("500"));
        assertThat(pct).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void computeReductionPercent_zeroBaselineReturnsZero() {
        BigDecimal pct = FinancedEmissionsService.computeReductionPercent(
                BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(pct).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createTarget_computesReductionPercentAndEmits() {
        AtomicLong seq = new AtomicLong(100);
        when(targetRepo.save(any(FinancedEmissionsTarget.class))).thenAnswer(assignId(seq));
        when(targetRepo.countByOrganisationIdAndTargetCode(anyLong(), anyString())).thenReturn(0L);

        FinancedEmissionsTargetRequest tReq = new FinancedEmissionsTargetRequest(
                null,
                "Net-zero by 2050", "الحياد الصافي بحلول 2050",
                2050, 2026,
                new BigDecimal("1500000"), BigDecimal.ZERO,
                Boolean.TRUE, "SBTI_1_5C"
        );
        FinancedEmissionsTargetResponse out = service.createTarget(tReq);

        assertThat(out.reductionPercent()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(out.alignmentFramework()).isEqualTo("SBTI_1_5C");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("FINANCED_EMISSIONS_TARGET_CREATED");
    }

    @Test
    void computeReductionAgainstTarget_emptyWhenNoTargets() {
        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of());

        List<TargetProgress> out = service.computeReductionAgainstTarget();
        assertThat(out).isEmpty();
    }

    @Test
    void computeReductionAgainstTarget_blendsAgainstLatestYear() {
        FinancedEmissionsTarget t = new FinancedEmissionsTarget();
        t.setId(10L); t.setExternalId(UUID.randomUUID());
        t.setOrganisationId(ORG_ID);
        t.setTargetCode("FE-TARGET-2030-INTERIM");
        t.setTitleEn("Interim 50% reduction by 2030");
        t.setTitleAr("خفض مرحلي بنسبة 50% بحلول 2030");
        t.setBaselineYear(2026);
        t.setTargetYear(2030);
        t.setBaselineEmissionsTco2e(new BigDecimal("1000"));
        t.setTargetEmissionsTco2e(new BigDecimal("500"));
        t.setReductionPercent(new BigDecimal("50.00"));
        t.setAlignmentFramework("NZBA");
        t.setSbtiAligned(Boolean.TRUE);

        FinancedEmissionsRecord r2026 = rec("APPROVED", "BUSINESS_LOANS",
                new BigDecimal("100"), new BigDecimal("0.500"),
                new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("700"));
        // total = 1500 * 0.5 = 750

        when(targetRepo.findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(ORG_ID))
                .thenReturn(List.of(t));
        when(recordRepo.findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(ORG_ID))
                .thenReturn(List.of(r2026));
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(ORG_ID, 2026))
                .thenReturn(List.of(r2026));

        List<TargetProgress> out = service.computeReductionAgainstTarget();

        assertThat(out).hasSize(1);
        TargetProgress p = out.get(0);
        assertThat(p.currentReportingYear()).isEqualTo(2026);
        assertThat(p.currentEmissionsTco2e()).isEqualByComparingTo(new BigDecimal("750.00"));
        // (1000 - 750)/1000 * 100 = 25.00
        assertThat(p.achievedReductionPercent()).isEqualByComparingTo(new BigDecimal("25.00"));
        // 25 / 50 = 0.5000
        assertThat(p.achievementRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    // ─── Tenant isolation ────────────────────────────────────────

    @Test
    void mustGet_throwsWhenWrongTenant() {
        when(recordRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, REC_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(REC_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void list_isOrgScoped() {
        TenantContext.setCurrentTenant(OTHER_ORG);
        when(recordRepo.findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(OTHER_ORG))
                .thenReturn(List.of());

        assertThat(service.list()).isEmpty();
        verify(recordRepo).findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(OTHER_ORG);
    }

    @Test
    void listForYear_callsYearScopedFinder() {
        when(recordRepo.findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(eq(ORG_ID), anyInt()))
                .thenReturn(List.of());

        service.listForYear(2025);
        verify(recordRepo).findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(ORG_ID, 2025);
    }
}
