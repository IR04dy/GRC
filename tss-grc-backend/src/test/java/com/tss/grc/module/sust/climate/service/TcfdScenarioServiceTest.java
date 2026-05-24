package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.ProbabilityWeightedExpectedLoss;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdLifecycleTransitionRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioRequest;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.TcfdScenarioResponse;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.repository.TcfdClimateScenarioRepository;
import com.tss.grc.module.sust.climate.repository.TcfdSectorImpactRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TcfdScenarioService — Sprint 11 (V186) test backlog.
 *
 * Locks the lifecycle (DRAFT → IN_REVIEW → BOARD_APPROVED → ARCHIVED),
 * the no-edit-on-approved rule, probability-weight blending math,
 * tenant isolation, and F8 event names.
 */
@ExtendWith(MockitoExtension.class)
class TcfdScenarioServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final UUID SCN_EX    = UUID.randomUUID();
    private static final Long SCN_ID    = 42L;

    @Mock private TcfdClimateScenarioRepository scenarioRepo;
    @Mock private TcfdSectorImpactRepository sectorRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TcfdScenarioService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static TcfdClimateScenario scenario(String stage, String type,
                                                BigDecimal expectedLoss,
                                                BigDecimal probabilityWeight) {
        TcfdClimateScenario s = new TcfdClimateScenario();
        s.setId(SCN_ID);
        s.setExternalId(SCN_EX);
        s.setOrganisationId(ORG_ID);
        s.setScenarioCode("TCFD-2026-ORDERLY");
        s.setTitleEn("Orderly net-zero by 2050");
        s.setTitleAr("انتقال منظَّم نحو الحياد الكربوني بحلول 2050");
        s.setScenarioType(type);
        s.setReferenceFramework("NGFS");
        s.setTimeHorizonYears(30);
        s.setGlobalWarmingCelsius(new BigDecimal("1.50"));
        s.setExpectedLossSarMillions(expectedLoss);
        s.setProbabilityWeight(probabilityWeight);
        s.setLifecycleStage(stage);
        return s;
    }

    private static TcfdScenarioRequest req() {
        return new TcfdScenarioRequest(
                null, "Orderly net-zero by 2050",
                "انتقال منظَّم نحو الحياد الكربوني بحلول 2050",
                "NGFS Orderly", "سيناريو NGFS المنظَّم",
                "ORDERLY", "NGFS", 30, new BigDecimal("1.50"),
                "{\"carbon_price_2030\": 200}",
                new BigDecimal("142.50"), new BigDecimal("1850.00"),
                new BigDecimal("0.500")
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

    // ─── Create ───────────────────────────────────────────────────

    @Test
    void create_stampsTenantAndDefaultsToDraft() {
        AtomicLong seq = new AtomicLong(100);
        when(scenarioRepo.save(any(TcfdClimateScenario.class))).thenAnswer(assignId(seq));

        TcfdScenarioResponse out = service.create(req());

        assertThat(out.lifecycleStage()).isEqualTo("DRAFT");
        assertThat(out.scenarioCode()).startsWith("TCFD-");

        ArgumentCaptor<TcfdClimateScenario> cap = ArgumentCaptor.forClass(TcfdClimateScenario.class);
        verify(scenarioRepo).save(cap.capture());
        TcfdClimateScenario saved = cap.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getScenarioType()).isEqualTo("ORDERLY");
        assertThat(saved.getReferenceFramework()).isEqualTo("NGFS");
        verify(audit).log(eq("CREATE"), eq("TcfdClimateScenario"), anyLong(), anyString());
    }

    @Test
    void create_publishesScenarioCreatedEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(scenarioRepo.save(any(TcfdClimateScenario.class))).thenAnswer(assignId(seq));

        service.create(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("TCFD_SCENARIO_CREATED");
        assertThat(ev.entityType()).isEqualTo("TcfdClimateScenario");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("scenarioType", "ORDERLY");
        assertThat(ev.payload()).containsEntry("referenceFramework", "NGFS");
        assertThat(ev.payload()).containsEntry("lifecycleStage", "DRAFT");
    }

    // ─── Update ───────────────────────────────────────────────────

    @Test
    void update_rejectsOnBoardApproved() {
        TcfdClimateScenario s = scenario("BOARD_APPROVED", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.update(SCN_EX, req()))
                .isInstanceOf(IllegalTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void update_rejectsOnArchived() {
        TcfdClimateScenario s = scenario("ARCHIVED", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.update(SCN_EX, req()))
                .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Lifecycle transitions ────────────────────────────────────

    @Test
    void transition_draftToInReviewEmitsSubmitted() {
        TcfdClimateScenario s = scenario("DRAFT", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));
        when(scenarioRepo.save(any(TcfdClimateScenario.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdScenarioResponse out = service.transition(SCN_EX,
                new TcfdLifecycleTransitionRequest("IN_REVIEW"), 7L);

        assertThat(out.lifecycleStage()).isEqualTo("IN_REVIEW");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("TCFD_SCENARIO_SUBMITTED");
        assertThat(cap.getValue().payload()).containsEntry("previousStage", "DRAFT");
    }

    @Test
    void transition_inReviewToBoardApprovedStampsApproverAndEmitsApproved() {
        TcfdClimateScenario s = scenario("IN_REVIEW", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));
        when(scenarioRepo.save(any(TcfdClimateScenario.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdScenarioResponse out = service.transition(SCN_EX,
                new TcfdLifecycleTransitionRequest("BOARD_APPROVED"), 99L);

        assertThat(out.lifecycleStage()).isEqualTo("BOARD_APPROVED");
        assertThat(out.approvedByUserId()).isEqualTo(99L);
        assertThat(out.approvedAt()).isNotNull();
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("TCFD_SCENARIO_APPROVED");
    }

    @Test
    void transition_boardApprovedToArchivedEmitsArchived() {
        TcfdClimateScenario s = scenario("BOARD_APPROVED", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));
        when(scenarioRepo.save(any(TcfdClimateScenario.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdScenarioResponse out = service.transition(SCN_EX,
                new TcfdLifecycleTransitionRequest("ARCHIVED"), 1L);

        assertThat(out.lifecycleStage()).isEqualTo("ARCHIVED");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("TCFD_SCENARIO_ARCHIVED");
    }

    @Test
    void transition_cannotArchiveDirectlyFromDraft() {
        // Hard gate: ARCHIVED is only reachable from BOARD_APPROVED.
        TcfdClimateScenario s = scenario("DRAFT", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() ->
                service.transition(SCN_EX, new TcfdLifecycleTransitionRequest("ARCHIVED"), 1L))
                .isInstanceOf(IllegalTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void transition_cannotReopenArchived() {
        TcfdClimateScenario s = scenario("ARCHIVED", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() ->
                service.transition(SCN_EX, new TcfdLifecycleTransitionRequest("IN_REVIEW"), 1L))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_inReviewBackToDraft() {
        TcfdClimateScenario s = scenario("IN_REVIEW", "ORDERLY",
                new BigDecimal("1850"), new BigDecimal("0.500"));
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.of(s));
        when(scenarioRepo.save(any(TcfdClimateScenario.class))).thenAnswer(inv -> inv.getArgument(0));

        TcfdScenarioResponse out = service.transition(SCN_EX,
                new TcfdLifecycleTransitionRequest("DRAFT"), 1L);
        assertThat(out.lifecycleStage()).isEqualTo("DRAFT");
    }

    // ─── Probability-weighting math ───────────────────────────────

    @Test
    void weightedExpectedLoss_blendsAcrossApprovedScenarios() {
        TcfdClimateScenario orderly = scenario("BOARD_APPROVED", "ORDERLY",
                new BigDecimal("1850.00"), new BigDecimal("0.500"));
        TcfdClimateScenario disorderly = scenario("BOARD_APPROVED", "DISORDERLY",
                new BigDecimal("4250.00"), new BigDecimal("0.350"));
        disorderly.setId(43L);
        disorderly.setExternalId(UUID.randomUUID());
        TcfdClimateScenario hot = scenario("BOARD_APPROVED", "HOT_HOUSE",
                new BigDecimal("3120.00"), new BigDecimal("0.150"));
        hot.setId(44L);
        hot.setExternalId(UUID.randomUUID());

        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                ORG_ID, "BOARD_APPROVED"))
                .thenReturn(List.of(orderly, disorderly, hot));

        ProbabilityWeightedExpectedLoss out = service.computeProbabilityWeightedExpectedLoss();

        // 1850·0.500 + 4250·0.350 + 3120·0.150 = 925 + 1487.5 + 468 = 2880.5
        assertThat(out.weightedExpectedLossSarMillions())
                .isEqualByComparingTo(new BigDecimal("2880.50"));
        assertThat(out.totalProbabilityWeight())
                .isEqualByComparingTo(new BigDecimal("1.000"));
        assertThat(out.approvedScenarioCount()).isEqualTo(3);
        assertThat(out.warning()).isNull();
    }

    @Test
    void weightedExpectedLoss_warnsWhenWeightsDontSumToOne() {
        TcfdClimateScenario orderly = scenario("BOARD_APPROVED", "ORDERLY",
                new BigDecimal("1850.00"), new BigDecimal("0.300"));
        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                ORG_ID, "BOARD_APPROVED"))
                .thenReturn(List.of(orderly));

        ProbabilityWeightedExpectedLoss out = service.computeProbabilityWeightedExpectedLoss();

        assertThat(out.totalProbabilityWeight()).isEqualByComparingTo(new BigDecimal("0.300"));
        assertThat(out.warning()).contains("Probability weights sum to");
    }

    @Test
    void weightedExpectedLoss_emptyWhenNoApprovedScenarios() {
        when(scenarioRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
                ORG_ID, "BOARD_APPROVED"))
                .thenReturn(List.of());

        ProbabilityWeightedExpectedLoss out = service.computeProbabilityWeightedExpectedLoss();

        assertThat(out.approvedScenarioCount()).isZero();
        assertThat(out.weightedExpectedLossSarMillions()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(out.warning()).contains("No BOARD_APPROVED scenarios");
    }

    // ─── Tenant isolation ─────────────────────────────────────────

    @Test
    void mustGet_throwsWhenWrongTenant() {
        when(scenarioRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SCN_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SCN_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void list_isOrgScoped() {
        TenantContext.setCurrentTenant(OTHER_ORG);
        when(scenarioRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(OTHER_ORG))
                .thenReturn(List.of());

        assertThat(service.list()).isEmpty();
        verify(scenarioRepo).findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(OTHER_ORG);
    }
}
