package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DriftObservationResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import com.tss.grc.module.ai.governance.repository.MlModelDriftObservationRepository;
import com.tss.grc.module.intake.spi.SystemIntakePort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 *  - CRITICAL severity files a RISK_ISSUE intake through SystemIntakePort
 *    and flips the parent model to REVISION
 *  - non-CRITICAL severity does not file an intake or flip stage
 *  - acknowledge() stamps acknowledged_at + by
 *  - threshold breach detection (metric vs threshold) is captured
 *  - reads are org-scoped
 *
 * <p>⚠⚠ <b>F2-175 — this class used to assert the defect as correct, and that is the reusable part.</b>
 * {@code recordCriticalDrift_autoOpensIssueAndFlipsModel} asserted
 * {@code getReferenceCode()).startsWith("ISS-")} and {@code getStatus()).isEqualTo("SUBMITTED")} — both
 * true of the code, both wrong about the platform: {@code ISS-} is a prefix no catalogue entry uses, and
 * every other submission lands in {@code TRIAGING}. It also asserted the payload merely
 * {@code contains("MODEL-2026-001")}, which a string built by concatenation satisfies whether or not it
 * is valid JSON — and it was not, for any title containing a tab.
 *
 * <p>Nothing here was careless. The assertions were written by reading the implementation, and a test
 * written that way can only ever agree with it. CLAUDE.md records the identical trap from F2-159, where
 * a test asserted that a {@code targetId} overload correctly discarded its {@code targetId}. 🔑 <b>The
 * question a test has to answer is not "does the code do this" but "should it" — and only the second one
 * can be got wrong in a way worth catching.</b> What the drift path owes intake is now asserted in
 * {@code IntakeSystemSubmissionTest}, against the catalogue rather than against the caller.
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
    @Mock private SystemIntakePort intake;
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
        verify(intake, never()).fileSystemSubmission(any());
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
    void recordCriticalDrift_filesIssueThroughThePortAndFlipsModel() {
        MlModel m = model("IN_PRODUCTION");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong driftSeq = new AtomicLong(100);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(driftSeq));
        when(intake.fileSystemSubmission(any())).thenReturn(
                new SystemIntakePort.Receipt(501L, "RI-K7MP-3QXZ", "RISK_OFFICER",
                        LocalDateTime.of(2026, 5, 15, 9, 0)));

        DriftObservationResponse out = service.record(MODEL_EX, req("CRITICAL", "PERFORMANCE_DEGRADATION"));

        // The drift row is stamped with the id the PORT reported — not one this module chose.
        assertThat(out.triggeredIssueId()).isEqualTo(501L);

        ArgumentCaptor<SystemIntakePort.SystemSubmission> cap =
                ArgumentCaptor.forClass(SystemIntakePort.SystemSubmission.class);
        verify(intake).fileSystemSubmission(cap.capture());
        SystemIntakePort.SystemSubmission s = cap.getValue();

        // ⚠ What this test asserts now is only what the drift monitor is entitled to decide: the
        // tenant, the kind, the channel, the severity, who is speaking, and the facts. The reference
        // code, the SLA, the assignee, the status and the history are the catalogue's — asserting them
        // here is what let ISS-/SUBMITTED/RISK_MANAGER look correct for as long as they did.
        assertThat(s.organisationId()).isEqualTo(ORG_ID);
        assertThat(s.kind()).isEqualTo("RISK_ISSUE");
        assertThat(s.channel()).isEqualTo("API_INGESTION");
        assertThat(s.severity()).isEqualTo("HIGH");
        assertThat(s.systemActor()).contains("drift monitor");

        // The payload is a Map now, so the facts are assertable as values rather than as substrings of
        // hand-built JSON — which is also why they can no longer be malformed.
        assertThat(s.payload())
                .containsEntry("trigger", "AI_MODEL_CRITICAL_DRIFT")
                .containsEntry("modelCode", "MODEL-2026-001")
                .containsEntry("observationType", "PERFORMANCE_DEGRADATION")
                .containsEntry("metricName", "auc_rolling_30d")
                .containsEntry("metricValue", new BigDecimal("0.831"));
        // The Arabic notes reached the register. The retired writer serialised notesEn and dropped
        // notesAr on the floor — on a platform whose bilingual rule makes Arabic mandatory.
        assertThat(s.payload()).containsEntry("notesAr", "ملاحظات");

        // ⚠⚠ The observation is saved EXACTLY ONCE, and this is the assertion the live probe bought.
        // The shape before F2-175 was save → open issue → mutate → save again, which worked only while
        // the issue was inserted in the same persistence context with no flush in between. Routing the
        // issue through the @Transactional port put a real boundary there, the intermediate flush
        // happened, and the second save's `UPDATE … WHERE row_version = <stale>` matched zero rows:
        // StaleObjectStateException, HTTP 500, on a governance escalation. CLAUDE.md has recorded the
        // ROWVERSION double-flush landmine for months; a mocked repository has no row_version, so the
        // suite was green over it and only a real database said otherwise.
        verify(driftRepo, org.mockito.Mockito.times(1)).save(any(MlModelDriftObservation.class));

        // …and the issue is filed BEFORE that save, which is what makes one save sufficient: the id is
        // on the entity when it is first written, rather than needing a second UPDATE to carry it.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(intake, driftRepo);
        order.verify(intake).fileSystemSubmission(any());
        order.verify(driftRepo).save(any(MlModelDriftObservation.class));

        // Model flipped via the service hook.
        verify(modelService).flipToRevisionDueToCriticalDrift(eqMl(m), anyString());

        // The audit line names the issue it raised. It named nothing before, so a CRITICAL drift left
        // no audit trace of the escalation at all.
        verify(audit).log(org.mockito.ArgumentMatchers.eq("CREATE"),
                org.mockito.ArgumentMatchers.eq("MlModelDriftObservation"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.contains("RI-K7MP-3QXZ"));

        // Event payload should reflect triggeredIssue=true.
        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().payload()).containsEntry("triggeredIssue", true);
        assertThat(evCap.getValue().payload()).containsEntry("severity", "CRITICAL");
    }

    // ─── F2-179 — the casing the CHECK constraint could not see ───
    //
    // The column's collation is case-INSENSITIVE, so CK_ml_drift_sev accepted 'critical', stored it
    // verbatim, and `"CRITICAL".equals(req.severity())` was then false. Reproduced live before the
    // fix: 201, row stored with severity = 'critical', triggered_issue_id NULL, and the parent model
    // still IN_PRODUCTION. Nothing anywhere reported a problem — the observation looked entirely
    // valid, which is the whole difficulty of the class.

    @Test
    @DisplayName("F2-179: a lower-case CRITICAL still escalates, and is stored canonically")
    void lowerCaseCriticalSeverityStillFilesTheIssueAndFlipsTheModel() {
        MlModel m = model("IN_PRODUCTION");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(new AtomicLong(200)));
        when(intake.fileSystemSubmission(any())).thenReturn(
                new SystemIntakePort.Receipt(777L, "RI-AAAA-BBBB", "RISK_OFFICER",
                        LocalDateTime.of(2026, 5, 15, 9, 0)));

        DriftObservationResponse out = service.record(
                MODEL_EX, req("critical", "performance_degradation"));

        verify(intake).fileSystemSubmission(any());
        verify(modelService).flipToRevisionDueToCriticalDrift(eqMl(m), anyString());
        assertThat(out.triggeredIssueId()).isEqualTo(777L);

        ArgumentCaptor<MlModelDriftObservation> cap =
                ArgumentCaptor.forClass(MlModelDriftObservation.class);
        verify(driftRepo).save(cap.capture());
        assertThat(cap.getValue().getSeverity()).as(
                "⚠ Normalised on WRITE, not compared with equalsIgnoreCase. The rejected alternative "
                + "fires the escalation but leaves 'critical' in the column, where the frontend's "
                + "Record<MlDriftSeverity, Label> lookup misses and the register renders a drift row "
                + "with no severity label.")
                .isEqualTo("CRITICAL");
        assertThat(cap.getValue().getObservationType()).isEqualTo("PERFORMANCE_DEGRADATION");
    }

    @Test
    @DisplayName("F2-179: the audit line records the stored value, not the caller's casing")
    void theAuditLineAgreesWithTheRowItDescribes() {
        MlModel m = model("MONITORING");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(driftRepo.save(any(MlModelDriftObservation.class))).thenAnswer(assignId(new AtomicLong(300)));

        service.record(MODEL_EX, req("moderate", "data_drift"));

        verify(audit).log(org.mockito.ArgumentMatchers.eq("CREATE"),
                org.mockito.ArgumentMatchers.eq("MlModelDriftObservation"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.contains("Drift DATA_DRIFT/MODERATE"));
    }

    @Test
    @DisplayName("F2-179: a severity outside the vocabulary is a 400 naming it, not a 409 from the CHECK")
    void anUnknownSeverityIsRefusedAtTheBoundary() {
        when(modelService.mustGet(MODEL_EX)).thenReturn(model("MONITORING"));

        assertThatThrownBy(() -> service.record(MODEL_EX, req("SEV1", "DATA_DRIFT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity")
                .hasMessageContaining("SEV1");
        assertThatThrownBy(() -> service.record(MODEL_EX, req("CRITICAL", "MODEL_ROT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observationType");
        // Nothing was written, and — the leg that matters on a refusal path — nothing was escalated.
        verify(driftRepo, org.mockito.Mockito.never()).save(any(MlModelDriftObservation.class));
        verify(intake, org.mockito.Mockito.never()).fileSystemSubmission(any());
    }

    @Test
    @DisplayName("F2-179: the declared vocabularies match the CHECK constraints they name")
    void theDriftVocabulariesMatchTheSchema() {
        assertThat(MlModelDriftService.SEVERITY.tokens())
                .isEqualTo(MigrationVocabularies.valuesOf("CK_ml_drift_sev"));
        assertThat(MlModelDriftService.OBSERVATION_TYPE.tokens())
                .isEqualTo(MigrationVocabularies.valuesOf("CK_ml_drift_type"));
        assertThat(MlModelDriftService.SEVERITY.tokens())
                .as("anti-vacuity, and the band the escalation turns on must be one the column holds")
                .contains(MlModelDriftService.ESCALATING_SEVERITY)
                .hasSize(5);
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
