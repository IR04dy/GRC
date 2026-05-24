package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import com.tss.grc.module.ai.governance.repository.MlModelDriftObservationRepository;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MlModelDriftService — Sprint 11 test backlog.
 *
 * Locks the post-market monitoring contract:
 *  - every record emits F8 MODEL_DRIFT_OBSERVED with severity
 *  - CRITICAL severity auto-opens an IntakeRecord (kind RISK_ISSUE)
 *    and flips the parent model to REVISION
 *  - non-CRITICAL severity does not auto-open Issue or flip stage
 *  - acknowledge() stamps acknowledged_at + by
 *  - threshold breach detection (metric vs threshold) is captured
 *  - reads are org-scoped
 */
@ExtendWith(MockitoExtension.class)
class MlModelDriftServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final UUID MODEL_EX  = UUID.randomUUID();
    private static final UUID OBS_EX    = UUID.randomUUID();
    private static final Long MODEL_ID  = 42L;

    @Mock private MlModelDriftObservationRepository driftRepo;
    @Mock private MlModelService modelService;
    @Mock private IntakeRecordRepository intakeRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MlModelDriftService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static MlModel model(String stage) {
        MlModel m = new MlModel();
        m.setId(MODEL_ID);
        m.setExternalId(MODEL_EX);
        m.setOrganisationId(ORG_ID);
        m.setModelCode("MODEL-2026-001");
        m.setTitleEn("Credit");
        m.setRiskTier("CRITICAL");
        m.setLifecycleStage(stage);
        return m;
    }

    private static DriftObservationRequest req(String severity, String type) {
        return new DriftObservationRequest(
                LocalDateTime.of(2026, 5, 1, 9, 0),
                type, severity,
                "auc_rolling_30d",
                new BigDecimal("0.831"),
                new BigDecimal("0.882"),
                new BigDecimal("0.860"),
                "Notes", "ملاحظات"
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

    // ─── Record (non-CRITICAL) ────────────────────────────────────

    @Test
    void recordModerateDrift_persistsAndEmitsButDoesNotOpenIssue() {
        MlModel m = model("MONITORING");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(seq));

        DriftObservationResponse out = service.record(MODEL_EX, req("MODERATE", "PERFORMANCE_DEGRADATION"));

        assertThat(out.severity()).isEqualTo("MODERATE");
        assertThat(out.triggeredIssueId()).isNull();
        verify(intakeRepo, never()).save(any(IntakeRecord.class));
        // Model stage NOT flipped.
        verify(modelService, never()).flipToRevisionDueToCriticalDrift(any(), anyString());
    }

    @Test
    void recordDrift_publishesEventWithSeverityAndMetric() {
        MlModel m = model("MONITORING");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(seq));

        service.record(MODEL_EX, req("MINOR", "DATA_DRIFT"));

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("MODEL_DRIFT_OBSERVED");
        assertThat(ev.payload()).containsEntry("severity", "MINOR");
        assertThat(ev.payload()).containsEntry("observationType", "DATA_DRIFT");
        assertThat(ev.payload()).containsEntry("metricName", "auc_rolling_30d");
        assertThat(ev.payload()).containsEntry("triggeredIssue", false);
    }

    @Test
    void recordDrift_capturesThresholdBreach() {
        MlModel m = model("MONITORING");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(seq));

        DriftObservationResponse out = service.record(MODEL_EX, req("MODERATE", "PERFORMANCE_DEGRADATION"));

        // metric (0.831) < threshold (0.860) — breach captured exactly in the row.
        assertThat(out.metricValue()).isEqualByComparingTo("0.831");
        assertThat(out.driftThreshold()).isEqualByComparingTo("0.860");
        assertThat(out.baselineValue()).isEqualByComparingTo("0.882");
    }

    // ─── Record (CRITICAL) — auto-open Issue + flip to REVISION ──

    @Test
    void recordCriticalDrift_autoOpensIssueAndFlipsModel() {
        MlModel m = model("IN_PRODUCTION");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong driftSeq = new AtomicLong(100);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(driftSeq));
        AtomicLong intakeSeq = new AtomicLong(500);
        when(intakeRepo.save(any(IntakeRecord.class))).thenAnswer(assignId(intakeSeq));

        DriftObservationResponse out = service.record(MODEL_EX, req("CRITICAL", "PERFORMANCE_DEGRADATION"));

        // Issue created with id from intake save.
        assertThat(out.triggeredIssueId()).isNotNull();
        ArgumentCaptor<IntakeRecord> intakeCap = ArgumentCaptor.forClass(IntakeRecord.class);
        verify(intakeRepo).save(intakeCap.capture());
        IntakeRecord issue = intakeCap.getValue();
        assertThat(issue.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(issue.getKind()).isEqualTo("RISK_ISSUE");
        assertThat(issue.getStatus()).isEqualTo("SUBMITTED");
        assertThat(issue.getReferenceCode()).startsWith("ISS-");
        assertThat(issue.getPayload()).contains("MODEL-2026-001");
        assertThat(issue.getPayload()).contains("AI_MODEL_CRITICAL_DRIFT");

        // Model flipped via the service hook.
        verify(modelService).flipToRevisionDueToCriticalDrift(eqMl(m), anyString());

        // Event payload should reflect triggeredIssue=true.
        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().payload()).containsEntry("triggeredIssue", true);
        assertThat(evCap.getValue().payload()).containsEntry("severity", "CRITICAL");
    }

    /** Mockito helper — we want exact identity match on the MlModel arg. */
    private static MlModel eqMl(MlModel expected) {
        return org.mockito.ArgumentMatchers.argThat(actual -> actual == expected);
    }

    // ─── Acknowledge ─────────────────────────────────────────────

    @Test
    void acknowledge_stampsTimestampsAndUser() {
        MlModel m = model("MONITORING");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);

        MlModelDriftObservation d = new MlModelDriftObservation();
        d.setId(7L);
        d.setExternalId(OBS_EX);
        d.setOrganisationId(ORG_ID);
        d.setModelId(MODEL_ID);
        d.setObservationType("DATA_DRIFT");
        d.setSeverity("MINOR");
        d.setMetricName("psi");
        d.setMetricValue(new BigDecimal("0.142"));
        d.setIsAcknowledged(false);

        when(driftRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, OBS_EX))
                .thenReturn(Optional.of(d));
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(inv -> inv.getArgument(0));

        DriftObservationResponse out = service.acknowledge(MODEL_EX, OBS_EX, 99L);

        assertThat(out.isAcknowledged()).isTrue();
        assertThat(out.acknowledgedByUserId()).isEqualTo(99L);
        assertThat(out.acknowledgedAt()).isNotNull();
    }

    // ─── Reads ──────────────────────────────────────────────────

    @Test
    void list_isOrgAndModelScoped() {
        MlModel m = model("MONITORING");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(driftRepo.findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByObservedAtDesc(
                ORG_ID, MODEL_ID))
                .thenReturn(List.of());

        assertThat(service.list(MODEL_EX)).isEmpty();
        verify(driftRepo).findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByObservedAtDesc(
                ORG_ID, MODEL_ID);
    }

    @Test
    void listUnacknowledged_isOrgScoped() {
        when(driftRepo.findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(ORG_ID))
                .thenReturn(List.of());

        assertThat(service.listUnacknowledged()).isEmpty();
        verify(driftRepo).findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(ORG_ID);
    }

    @Test
    void listUnacknowledged_secondTenantSeesOwnRowsOnly() {
        TenantContext.setCurrentTenant(OTHER_ORG);
        when(driftRepo.findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(OTHER_ORG))
                .thenReturn(List.of());

        service.listUnacknowledged();
        verify(driftRepo).findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(OTHER_ORG);
    }
}
