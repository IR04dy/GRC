package com.tss.grc.module.bcm.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeLossLineDto;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeRequest;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeResponse;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.CfoApprovalRequest;
import com.tss.grc.module.bcm.entity.BiaQuantitative;
import com.tss.grc.module.bcm.entity.BiaQuantitativeLossLine;
import com.tss.grc.module.bcm.repository.BiaQuantitativeRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuantitativeBiaService — Slice 9-C unit tests.
 *
 * <p>Covers the contract we promise the controller and the parts that
 * a Saudi-bank CFO actually relies on:
 * <ul>
 *   <li>create + recompute totals + breach detection round-trip</li>
 *   <li>findMtpdBreachBand for breach + no-breach cases</li>
 *   <li>status state machine — reject invalid transitions</li>
 *   <li>tenant isolation — queries always filter by org</li>
 *   <li>CFO approve / reject branches</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuantitativeBiaServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private BiaQuantitativeRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private QuantitativeBiaService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── Fixtures ──────────────────────────────────────────────────────

    private static BiaQuantitativeRequest sampleRequest(BigDecimal ceiling, BigDecimal band24h, BigDecimal band72h) {
        return new BiaQuantitativeRequest(
            42L,                              // processId — SARIE feed
            "BIA-Q-2026-TEST",                // assessment code
            LocalDate.of(2026, 4, 15),
            2026,
            "SAR",
            "SAMA ORF §5.3 quantification — SARIE/SADAD/mada retail payments core processing.",
            "تحليل كمي وفق إطار ساما للمرونة التشغيلية §5.3.",
            ceiling,
            "Layla Al-Otaibi (BCM Lead)",
            null,
            List.of(
                new BiaQuantitativeLossLineDto(
                    null, "24h", "REVENUE_LOSS", band24h,
                    "Lost mada POS + SADAD interchange — Saudi-bank baseline",
                    "خسارة عمولات مدى وسداد — الأساس المصرفي السعودي",
                    "HIGH", true),
                new BiaQuantitativeLossLineDto(
                    null, "72h", "REVENUE_LOSS", band72h,
                    "Three-day SARIE outage — interbank float lost",
                    "انقطاع سريع لثلاثة أيام — خسارة العائم بين المصارف",
                    "MEDIUM", true)
            )
        );
    }

    private static BiaQuantitative existing(String status) {
        BiaQuantitative h = new BiaQuantitative();
        h.setId(101L);
        h.setOrganisationId(ORG_ID);
        h.setExternalId(UUID.randomUUID());
        h.setProcessId(42L);
        h.setAssessmentCode("BIA-Q-2026-EXIST");
        h.setAssessmentDate(LocalDate.of(2026, 4, 15));
        h.setCycleYear(2026);
        h.setCurrency("SAR");
        h.setStatus(status);
        h.setMaxTolerableLossSar(new BigDecimal("25000000"));
        return h;
    }

    // ── Create / update happy path ───────────────────────────────────

    @Test void create_savesWithTotalsAndBreachComputed() {
        BiaQuantitativeRequest req = sampleRequest(
            new BigDecimal("25000000"),     // ceiling 25M
            new BigDecimal("13000000"),     // 24h: 13M (cumulative 13M — under ceiling)
            new BigDecimal("20000000"));    // 72h: 20M (cumulative 33M — breaches)
        when(repo.existsByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(any(), any())).thenReturn(false);
        when(repo.save(any(BiaQuantitative.class))).thenAnswer(inv -> {
            BiaQuantitative h = inv.getArgument(0);
            h.setId(101L);
            return h;
        });

        BiaQuantitativeResponse out = service.create(req);

        assertThat(out.status()).isEqualTo("DRAFT");
        assertThat(out.totalLoss24hSar()).isEqualByComparingTo("13000000");
        assertThat(out.totalLoss72hSar()).isEqualByComparingTo("20000000");
        assertThat(out.mtpdBreachAt()).isEqualTo("72h");
        verify(publisher).publishEvent(any(DomainEvent.class));
        verify(audit).log(any(String.class), any(String.class), any(Long.class), any(String.class));
    }

    @Test void create_duplicateAssessmentCodeRejected() {
        BiaQuantitativeRequest req = sampleRequest(
            new BigDecimal("25000000"), new BigDecimal("1"), new BigDecimal("1"));
        when(repo.existsByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(ORG_ID, "BIA-Q-2026-TEST"))
            .thenReturn(true);
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void update_blockedOnApprovedRecord() {
        BiaQuantitative h = existing("CFO_APPROVED");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        BiaQuantitativeRequest req = sampleRequest(
            new BigDecimal("25000000"), new BigDecimal("1"), new BigDecimal("1"));
        assertThatThrownBy(() -> service.update(h.getExternalId(), req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CFO_APPROVED");
    }

    @Test void update_allowedInDraft() {
        BiaQuantitative h = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        when(repo.save(any(BiaQuantitative.class))).thenAnswer(inv -> inv.getArgument(0));
        BiaQuantitativeRequest req = sampleRequest(
            new BigDecimal("25000000"), new BigDecimal("13000000"), new BigDecimal("20000000"));
        BiaQuantitativeResponse out = service.update(h.getExternalId(), req);
        assertThat(out.totalLoss24hSar()).isEqualByComparingTo("13000000");
        assertThat(out.mtpdBreachAt()).isEqualTo("72h");
    }

    // ── recomputeTotals + findMtpdBreachBand ─────────────────────────

    @Test void recomputeTotals_sumsLinesPerBand() {
        BiaQuantitative h = new BiaQuantitative();
        h.setOrganisationId(ORG_ID);
        h.addLossLine(line("24h", "REVENUE_LOSS", "5000000"));
        h.addLossLine(line("24h", "REGULATORY_FINE", "2000000"));
        h.addLossLine(line("72h", "REVENUE_LOSS", "10000000"));
        h.addLossLine(line("1m",  "REVENUE_LOSS", "150000000"));

        service.recomputeTotals(h);

        assertThat(h.getTotalLoss24hSar()).isEqualByComparingTo("7000000");
        assertThat(h.getTotalLoss72hSar()).isEqualByComparingTo("10000000");
        assertThat(h.getTotalLoss1wSar()).isNull();        // never modelled
        assertThat(h.getTotalLoss2wSar()).isNull();
        assertThat(h.getTotalLoss1mSar()).isEqualByComparingTo("150000000");
    }

    @Test void findMtpdBreachBand_returnsFirstBandPastCeiling() {
        BiaQuantitative h = new BiaQuantitative();
        h.setMaxTolerableLossSar(new BigDecimal("25000000"));
        h.setTotalLoss24hSar(new BigDecimal("13000000"));    // cumulative 13M
        h.setTotalLoss72hSar(new BigDecimal("20000000"));    // cumulative 33M → breach
        h.setTotalLoss1wSar(new BigDecimal("40000000"));
        h.setTotalLoss2wSar(new BigDecimal("50000000"));
        h.setTotalLoss1mSar(new BigDecimal("100000000"));

        assertThat(service.findMtpdBreachBand(h)).isEqualTo("72h");
    }

    @Test void findMtpdBreachBand_nullWhenWithinTolerance() {
        BiaQuantitative h = new BiaQuantitative();
        h.setMaxTolerableLossSar(new BigDecimal("500000000"));    // very high ceiling
        h.setTotalLoss24hSar(new BigDecimal("13000000"));
        h.setTotalLoss72hSar(new BigDecimal("20000000"));
        h.setTotalLoss1wSar(new BigDecimal("40000000"));
        h.setTotalLoss2wSar(new BigDecimal("50000000"));
        h.setTotalLoss1mSar(new BigDecimal("100000000"));

        assertThat(service.findMtpdBreachBand(h)).isNull();
    }

    @Test void findMtpdBreachBand_nullWhenNoCeiling() {
        BiaQuantitative h = new BiaQuantitative();
        h.setMaxTolerableLossSar(null);
        h.setTotalLoss24hSar(new BigDecimal("9999999999"));
        assertThat(service.findMtpdBreachBand(h)).isNull();
    }

    @Test void findMtpdBreachBand_immediateAt24h() {
        // Headline-grade outage: even the 24h band alone exceeds ceiling.
        BiaQuantitative h = new BiaQuantitative();
        h.setMaxTolerableLossSar(new BigDecimal("5000000"));
        h.setTotalLoss24hSar(new BigDecimal("12000000"));
        assertThat(service.findMtpdBreachBand(h)).isEqualTo("24h");
    }

    // ── Status state machine ─────────────────────────────────────────

    @Test void submitForReview_draftToInReviewOk() {
        BiaQuantitative h = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        when(repo.save(any(BiaQuantitative.class))).thenAnswer(inv -> inv.getArgument(0));
        BiaQuantitativeResponse out = service.submitForReview(h.getExternalId());
        assertThat(out.status()).isEqualTo("IN_REVIEW");
    }

    @Test void cfoApprove_blockedFromDraft() {
        BiaQuantitative h = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        CfoApprovalRequest req = new CfoApprovalRequest( "ok", "APPROVE", null);
        assertThatThrownBy(() -> service.cfoApprove(h.getExternalId(), req))
            .isInstanceOf(IllegalTransitionException.class);
        verify(repo, never()).save(any());
    }

    @Test void cfoApprove_happyPathFromCfoReview() {
        BiaQuantitative h = existing("CFO_REVIEW");
        h.setTotalLoss24hSar(new BigDecimal("13000000"));
        h.setTotalLoss72hSar(new BigDecimal("20000000"));
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        when(repo.save(any(BiaQuantitative.class))).thenAnswer(inv -> inv.getArgument(0));

        CfoApprovalRequest req = new CfoApprovalRequest(
            "Approved with dual-site remediation mandated.",
            "APPROVE", null);
        f223AuthAs("Faisal Al-Subaie (Group CFO)", 9L);
        BiaQuantitativeResponse out = service.cfoApprove(h.getExternalId(), req);

        assertThat(out.status()).isEqualTo("CFO_APPROVED");
        // F2-223 — the CFO is the authenticated principal, not a name in the body.
        assertThat(out.cfoApproverName()).isEqualTo("Faisal Al-Subaie (Group CFO)");
        assertThat(out.cfoApprovedAt()).isNotNull();
        assertThat(out.mtpdBreachAt()).isEqualTo("72h");      // recomputed at approval-time
    }

    @Test void cfoApprove_rejectRequiresReason() {
        BiaQuantitative h = existing("CFO_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        CfoApprovalRequest req = new CfoApprovalRequest( null, "REJECT", null);
        assertThatThrownBy(() -> service.cfoApprove(h.getExternalId(), req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rejectionReason");
    }

    @Test void cfoApprove_rejectStampsReason() {
        BiaQuantitative h = existing("CFO_REVIEW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        when(repo.save(any(BiaQuantitative.class))).thenAnswer(inv -> inv.getArgument(0));
        CfoApprovalRequest req = new CfoApprovalRequest( null, "REJECT",
            "Revenue baseline understated — re-do with audited annual report figures.");
        BiaQuantitativeResponse out = service.cfoApprove(h.getExternalId(), req);
        assertThat(out.status()).isEqualTo("REJECTED");
        assertThat(out.rejectionReason()).contains("Revenue baseline");
    }

    @Test void boardApprove_requiresMinutesRef() {
        // Validation happens before the load — no repo stub needed.
        assertThatThrownBy(() -> service.boardApprove(UUID.randomUUID(), ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minutesRef");
        verify(repo, never()).save(any());
    }

    @Test void boardApprove_movesCfoApprovedToBoardApproved() {
        BiaQuantitative h = existing("CFO_APPROVED");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        when(repo.save(any(BiaQuantitative.class))).thenAnswer(inv -> inv.getArgument(0));
        BiaQuantitativeResponse out = service.boardApprove(h.getExternalId(),
            "Board minutes 2026-Q2 §4.3 (Risk Committee)");
        assertThat(out.status()).isEqualTo("BOARD_APPROVED");
        assertThat(out.boardMinutesRef()).contains("2026-Q2");
    }

    @Test void boardApprove_blockedFromDraft() {
        BiaQuantitative h = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, h.getExternalId()))
            .thenReturn(Optional.of(h));
        assertThatThrownBy(() -> service.boardApprove(h.getExternalId(), "any"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ── Tenant isolation ─────────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(ORG_ID))
            .thenReturn(List.of());
        service.list();
        verify(repo, times(1)).findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(ORG_ID);
    }

    @Test void get_alwaysFiltersByOrganisation() {
        UUID ext = UUID.randomUUID();
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ext))
            .thenReturn(Optional.of(existing("DRAFT")));
        service.get(ext);
        verify(repo).findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ext);
    }

    @Test void create_setsOrgFromTenantContext() {
        BiaQuantitativeRequest req = sampleRequest(
            new BigDecimal("25000000"), new BigDecimal("1000"), new BigDecimal("1000"));
        when(repo.existsByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(any(), any())).thenReturn(false);
        ArgumentCaptor<BiaQuantitative> captor = ArgumentCaptor.forClass(BiaQuantitative.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> {
            BiaQuantitative h = inv.getArgument(0);
            h.setId(202L);
            return h;
        });

        service.create(req);

        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test void create_propagatesOrgToLossLines() {
        BiaQuantitativeRequest req = sampleRequest(
            new BigDecimal("25000000"), new BigDecimal("1000"), new BigDecimal("1000"));
        when(repo.existsByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(any(), any())).thenReturn(false);
        ArgumentCaptor<BiaQuantitative> captor = ArgumentCaptor.forClass(BiaQuantitative.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> {
            BiaQuantitative h = inv.getArgument(0);
            h.setId(303L);
            return h;
        });

        service.create(req);

        BiaQuantitative saved = captor.getValue();
        assertThat(saved.getLossLines()).isNotEmpty();
        assertThat(saved.getLossLines())
            .allSatisfy(line -> assertThat(line.getOrganisationId()).isEqualTo(ORG_ID));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static BiaQuantitativeLossLine line(String band, String type, String amount) {
        BiaQuantitativeLossLine l = new BiaQuantitativeLossLine();
        l.setTimeBand(band);
        l.setLossType(type);
        l.setAmountSar(new BigDecimal(amount));
        l.setConfidence("MEDIUM");
        l.setIsEstimate(true);
        l.setOrganisationId(ORG_ID);
        return l;
    }

    /**
     * F2-223 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void f223AuthAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }
}
