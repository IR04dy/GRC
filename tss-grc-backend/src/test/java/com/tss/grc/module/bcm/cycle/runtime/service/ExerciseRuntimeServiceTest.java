package com.tss.grc.module.bcm.cycle.runtime.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.ExerciseScenarioInject;
import com.tss.grc.module.bcm.cycle.repository.ExerciseScenarioInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.CustomInjectRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.InjectView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunDetail;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunTimeline;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.StartRunRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.TimelineEntry;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.TransitionRequest;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunResponse;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObservationRepository;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObserverAssignmentRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExerciseRuntimeService} (Slice 10-B).
 *
 * <p>The service has six contracts the rest of the slice depends on:
 * <ul>
 *   <li>{@code startRun} clones source injects + writes audit + F8 + sets IN_PROGRESS.</li>
 *   <li>{@code dispatchInject} records actual_dispatched_at, transitions
 *       PENDING → DISPATCHED, emits F7+F8, and broadcasts on the run topic.</li>
 *   <li>{@code recordResponse} computes response_time_seconds against the
 *       inject's actual_dispatched_at and auto-ACKs the inject.</li>
 *   <li>State machine — PAUSE/RESUME/END/ABORT validated; invalid throws.</li>
 *   <li>{@code addCustomInject} creates a row with sourceInjectId == null.</li>
 *   <li>Tenant isolation — cross-tenant access throws 404.</li>
 *   <li>Timeline endpoint returns chronological order including custom injects.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExerciseRuntimeServiceTest {

    private static final Long ORG_ID = 9L;
    private static final Long EXERCISE_ID = 700L;
    private static final Long SCENARIO_ID = 401L;
    private static final Long RUN_ID = 5000L;

    @Mock private ExerciseRunRepository runRepo;
    @Mock private ExerciseRunInjectRepository runInjectRepo;
    @Mock private ExerciseRunResponseRepository responseRepo;
    @Mock private BcmExerciseRepository exerciseRepo;
    @Mock private ExerciseScenarioInjectRepository scenarioInjectRepo;
    // Slice 10-C — observer integration; empty by default so 10-B tests are unaffected.
    @Mock private ExerciseObservationRepository observationRepo;
    @Mock private ExerciseObserverAssignmentRepository observerAssignmentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private RealtimeBroadcaster realtime;
    @InjectMocks private ExerciseRuntimeService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────────

    private static BcmExercise exerciseFixture(Long scenarioId) {
        BcmExercise ex = new BcmExercise();
        ex.setId(EXERCISE_ID);
        ex.setOrganisationId(ORG_ID);
        ex.setExerciseNameEn("Q2 Ransom Tabletop");
        ex.setExerciseNameAr("تمرين الفدية للربع الثاني");
        ex.setExerciseType("TABLETOP");
        ex.setStatus("SCHEDULED");
        ex.setScenarioId(scenarioId);
        return ex;
    }

    private static ExerciseScenarioInject sourceInjectFixture(long id, int order, int offset, String type) {
        ExerciseScenarioInject i = new ExerciseScenarioInject();
        i.setId(id);
        i.setScenarioId(SCENARIO_ID);
        i.setInjectOrder(order);
        i.setTimeOffsetMinutes(offset);
        i.setInjectType(type);
        i.setInjectTextEn("Inject " + order + " text EN");
        i.setInjectTextAr("نص الإدخال رقم " + order);
        i.setExpectedActionEn("Expected action " + order);
        i.setExpectedActionAr("الإجراء المتوقع " + order);
        return i;
    }

    private static ExerciseRun runFixture(String status) {
        ExerciseRun r = new ExerciseRun();
        r.setId(RUN_ID);
        r.setOrganisationId(ORG_ID);
        r.setExerciseId(EXERCISE_ID);
        r.setRunCode("RUN-9-EX700-001");
        r.setStatus(status);
        r.setStartedAt(LocalDateTime.now().minusMinutes(30));
        r.setChannel("IN_PERSON");
        r.setParticipantCount(8);
        return r;
    }

    private static ExerciseRunInject runInjectFixture(Long id, Long runId, String status, Long sourceId) {
        ExerciseRunInject ri = new ExerciseRunInject();
        ri.setId(id);
        ri.setOrganisationId(ORG_ID);
        ri.setExerciseRunId(runId);
        ri.setSourceInjectId(sourceId);
        ri.setPlannedTimeOffsetMinutes(10);
        ri.setInjectType("ESCALATION");
        ri.setInjectTextEn("RI EN " + id);
        ri.setInjectTextAr("RI AR " + id);
        ri.setStatus(status);
        return ri;
    }

    /**
     * Wires the run-inject save to assign a deterministic incrementing id and
     * captures the saved rows for later inspection (the service uses these for
     * the cloned inject count + audit messages).
     */
    private void stubInjectSaveAssignsId(List<ExerciseRunInject> captured) {
        AtomicLong seq = new AtomicLong(10_000L);
        when(runInjectRepo.save(any(ExerciseRunInject.class))).thenAnswer(inv -> {
            ExerciseRunInject ri = inv.getArgument(0);
            if (ri.getId() == null) ri.setId(seq.getAndIncrement());
            captured.add(ri);
            return ri;
        });
    }

    // ─── startRun ────────────────────────────────────────────────────────

    @Test
    void startRun_clones_injects_from_linked_scenario() {
        when(exerciseRepo.findById(EXERCISE_ID))
            .thenReturn(Optional.of(exerciseFixture(SCENARIO_ID)));
        when(runRepo.save(any(ExerciseRun.class))).thenAnswer(inv -> {
            ExerciseRun r = inv.getArgument(0);
            r.setId(RUN_ID);
            return r;
        });
        when(scenarioInjectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of(
                sourceInjectFixture(1L, 1, 0, "INFORMATION"),
                sourceInjectFixture(2L, 2, 10, "ESCALATION"),
                sourceInjectFixture(3L, 3, 30, "DECISION"),
                sourceInjectFixture(4L, 4, 60, "REGULATOR")));
        List<ExerciseRunInject> captured = new ArrayList<>();
        stubInjectSaveAssignsId(captured);

        RunSummary out = service.startRun(new StartRunRequest(
            EXERCISE_ID, 42L, "HYBRID", 8, "Notes", "ملاحظات", null));

        assertThat(out.id()).isEqualTo(RUN_ID);
        assertThat(out.status()).isEqualTo("IN_PROGRESS");
        assertThat(out.channel()).isEqualTo("HYBRID");
        assertThat(out.facilitatorUserId()).isEqualTo(42L);
        assertThat(out.runCode()).startsWith("RUN-9-EX700-");

        assertThat(captured).hasSize(4);
        assertThat(captured.get(0).getSourceInjectId()).isEqualTo(1L);
        assertThat(captured.get(0).getInjectType()).isEqualTo("INFORMATION");
        assertThat(captured.get(0).getInjectTextAr()).contains("نص الإدخال");
        assertThat(captured.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(captured.get(2).getPlannedTimeOffsetMinutes()).isEqualTo(30);
        assertThat(captured.get(3).getInjectType()).isEqualTo("REGULATOR");
    }

    @Test
    void startRun_without_scenario_link_creates_run_with_no_injects() {
        BcmExercise ex = exerciseFixture(null);   // no scenario_id
        when(exerciseRepo.findById(EXERCISE_ID)).thenReturn(Optional.of(ex));
        when(runRepo.save(any(ExerciseRun.class))).thenAnswer(inv -> {
            ExerciseRun r = inv.getArgument(0);
            r.setId(RUN_ID);
            return r;
        });

        RunSummary out = service.startRun(new StartRunRequest(
            EXERCISE_ID, null, null, null, null, null, null));

        assertThat(out.status()).isEqualTo("IN_PROGRESS");
        assertThat(out.channel()).isEqualTo("IN_PERSON");   // default
        verify(scenarioInjectRepo, never()).findByScenarioIdOrderByInjectOrderAsc(anyLong());
        verify(runInjectRepo, never()).save(any(ExerciseRunInject.class));
    }

    @Test
    void startRun_writes_F7_audit_and_emits_F8_event() {
        when(exerciseRepo.findById(EXERCISE_ID))
            .thenReturn(Optional.of(exerciseFixture(SCENARIO_ID)));
        when(runRepo.save(any(ExerciseRun.class))).thenAnswer(inv -> {
            ExerciseRun r = inv.getArgument(0);
            r.setId(RUN_ID);
            return r;
        });
        when(scenarioInjectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of(sourceInjectFixture(1L, 1, 0, "INFORMATION")));
        stubInjectSaveAssignsId(new ArrayList<>());

        service.startRun(new StartRunRequest(EXERCISE_ID, 42L, null, 8, null, null, null));

        verify(audit).log(eq("CREATE"), eq("ExerciseRun"), eq(RUN_ID), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evt.capture());
        DomainEvent fired = evt.getValue();
        assertThat(fired.triggerEvent()).isEqualTo("EXERCISE_RUN_STARTED");
        assertThat(fired.entityType()).isEqualTo("ExerciseRun");
        assertThat(fired.entityId()).isEqualTo(RUN_ID);
        assertThat(fired.organisationId()).isEqualTo(ORG_ID);
        assertThat(fired.payload())
            .containsEntry("runId", String.valueOf(RUN_ID))
            .containsEntry("injectCount", "1");
    }

    @Test
    void startRun_rejects_unknown_channel() {
        when(exerciseRepo.findById(EXERCISE_ID))
            .thenReturn(Optional.of(exerciseFixture(null)));

        assertThatThrownBy(() -> service.startRun(new StartRunRequest(
            EXERCISE_ID, null, "TEAMS_MEETING", null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("channel");
    }

    @Test
    void startRun_404_when_exercise_belongs_to_other_tenant() {
        BcmExercise ex = exerciseFixture(SCENARIO_ID);
        ex.setOrganisationId(99L);   // different tenant
        when(exerciseRepo.findById(EXERCISE_ID)).thenReturn(Optional.of(ex));

        assertThatThrownBy(() -> service.startRun(new StartRunRequest(
            EXERCISE_ID, null, null, null, null, null, null)))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("not found for tenant");

        verify(runRepo, never()).save(any());
    }

    // ─── dispatchInject ──────────────────────────────────────────────────

    @Test
    void dispatchInject_sets_timestamp_and_status_and_emits_F8() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        ExerciseRunInject ri = runInjectFixture(700L, RUN_ID, "PENDING", 1L);
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runInjectRepo.findByIdAndOrganisationId(700L, ORG_ID)).thenReturn(Optional.of(ri));
        when(runInjectRepo.save(any(ExerciseRunInject.class))).thenAnswer(inv -> inv.getArgument(0));

        InjectView out = service.dispatchInject(RUN_ID, 700L);

        assertThat(out.status()).isEqualTo("DISPATCHED");
        assertThat(out.actualDispatchedAt()).isNotNull();

        verify(audit).log(eq("UPDATE"), eq("ExerciseRunInject"), eq(700L), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("EXERCISE_INJECT_DISPATCHED");
        assertThat(evt.getValue().payload())
            .containsEntry("runId", String.valueOf(RUN_ID))
            .containsEntry("runInjectId", "700");

        // WebSocket broadcast
        verify(realtime).broadcastOrgEvent(eq(ORG_ID), eq("exercise-run." + RUN_ID), any());
    }

    @Test
    void dispatchInject_rejects_when_run_not_in_progress() {
        ExerciseRun run = runFixture("PAUSED");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.dispatchInject(RUN_ID, 700L))
            .isInstanceOf(IllegalTransitionException.class);

        verify(runInjectRepo, never()).save(any());
    }

    @Test
    void dispatchInject_rejects_when_inject_already_dispatched() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        ExerciseRunInject ri = runInjectFixture(700L, RUN_ID, "DISPATCHED", 1L);
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runInjectRepo.findByIdAndOrganisationId(700L, ORG_ID)).thenReturn(Optional.of(ri));

        assertThatThrownBy(() -> service.dispatchInject(RUN_ID, 700L))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── recordResponse ──────────────────────────────────────────────────

    @Test
    void recordResponse_computes_response_time_seconds_and_acks_inject() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        ExerciseRunInject ri = runInjectFixture(700L, RUN_ID, "DISPATCHED", 1L);
        ri.setActualDispatchedAt(LocalDateTime.now().minusSeconds(45));
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runInjectRepo.findByIdAndOrganisationId(700L, ORG_ID)).thenReturn(Optional.of(ri));
        when(responseRepo.save(any(ExerciseRunResponse.class))).thenAnswer(inv -> {
            ExerciseRunResponse r = inv.getArgument(0);
            r.setId(900L);
            return r;
        });
        when(runInjectRepo.save(any(ExerciseRunInject.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseView out = service.recordResponse(RUN_ID, 700L,
            new ResponseRequest(7L, "I'd isolate core banking",
                "سأعزل النظام المصرفي", "ISOLATE", "عزل"));

        assertThat(out.id()).isEqualTo(900L);
        // Allow a 5-second tolerance — between 40 and 60 seconds is reasonable
        assertThat(out.responseTimeSeconds()).isBetween(40, 60);
        assertThat(out.participantUserId()).isEqualTo(7L);
        assertThat(out.responseTextEn()).contains("isolate");

        // Auto-ACK: the inject must have moved to ACKNOWLEDGED.
        ArgumentCaptor<ExerciseRunInject> updated = ArgumentCaptor.forClass(ExerciseRunInject.class);
        verify(runInjectRepo).save(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo("ACKNOWLEDGED");

        // F7 + F8
        verify(audit).log(eq("CREATE"), eq("ExerciseRunResponse"), eq(900L), anyString());
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("EXERCISE_RESPONSE_RECORDED");
    }

    @Test
    void recordResponse_rejects_when_inject_not_dispatched() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        ExerciseRunInject ri = runInjectFixture(700L, RUN_ID, "PENDING", 1L);
        ri.setActualDispatchedAt(null);
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runInjectRepo.findByIdAndOrganisationId(700L, ORG_ID)).thenReturn(Optional.of(ri));

        assertThatThrownBy(() -> service.recordResponse(RUN_ID, 700L,
            new ResponseRequest(7L, "early", null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has not been dispatched");
    }

    // ─── State machine ───────────────────────────────────────────────────

    @Test
    void transition_pause_resume_end_round_trip() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runRepo.save(any(ExerciseRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(exerciseRepo.findById(EXERCISE_ID))
            .thenReturn(Optional.of(exerciseFixture(SCENARIO_ID)));

        // PAUSE → PAUSED
        RunSummary out = service.transition(RUN_ID, "pause", null);
        assertThat(out.status()).isEqualTo("PAUSED");

        // RESUME → IN_PROGRESS  (we have to re-prime since `run` was mutated)
        run.setStatus("PAUSED");
        out = service.transition(RUN_ID, "RESUME", null);
        assertThat(out.status()).isEqualTo("IN_PROGRESS");

        // END → COMPLETED  (with summary)
        run.setStatus("IN_PROGRESS");
        out = service.transition(RUN_ID, "END",
            new TransitionRequest("All injects resolved.", "تمّت معالجة جميع الإدخالات."));
        assertThat(out.status()).isEqualTo("COMPLETED");
        assertThat(out.endedAt()).isNotNull();
    }

    @Test
    void transition_invalid_action_throws() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.transition(RUN_ID, "STOP", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown action");
    }

    @Test
    void transition_from_completed_to_anything_throws() {
        ExerciseRun run = runFixture("COMPLETED");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.transition(RUN_ID, "PAUSE", null))
            .isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> service.transition(RUN_ID, "END", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_abort_from_in_progress_emits_aborted_event() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runRepo.save(any(ExerciseRun.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(RUN_ID, "ABORT", null);

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evt.capture());
        assertThat(evt.getAllValues())
            .anySatisfy(e -> assertThat(e.triggerEvent()).isEqualTo("EXERCISE_RUN_ABORTED"));
    }

    // ─── Custom inject ──────────────────────────────────────────────────

    @Test
    void addCustomInject_creates_row_with_null_source_id() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        List<ExerciseRunInject> captured = new ArrayList<>();
        stubInjectSaveAssignsId(captured);

        InjectView out = service.addCustomInject(RUN_ID, new CustomInjectRequest(
            "On-the-fly inject: media leak",
            "موقف طارئ: تسريب إعلامي",
            "DPO drafts holding statement",
            "مسؤول حماية البيانات يعدّ بياناً تحفّظياً",
            null,
            null,
            false));   // don't auto-dispatch — keep PENDING

        assertThat(out.injectType()).isEqualTo("CUSTOM");
        assertThat(out.sourceInjectId()).isNull();
        assertThat(out.status()).isEqualTo("PENDING");
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getSourceInjectId()).isNull();
        assertThat(captured.get(0).getInjectTextAr()).contains("تسريب");

        verify(audit).log(eq("CREATE"), eq("ExerciseRunInject"), anyLong(), anyString());
    }

    @Test
    void addCustomInject_with_dispatchNow_dispatches_immediately() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        List<ExerciseRunInject> captured = new ArrayList<>();
        stubInjectSaveAssignsId(captured);
        // After save, the dispatchInject step needs the inject to be findable.
        when(runInjectRepo.findByIdAndOrganisationId(anyLong(), eq(ORG_ID)))
            .thenAnswer(inv -> {
                Long id = inv.getArgument(0);
                return captured.stream().filter(r -> id.equals(r.getId())).findFirst();
            });

        InjectView out = service.addCustomInject(RUN_ID, new CustomInjectRequest(
            "Custom now", null, null, null, "INFORMATION", null, true));

        assertThat(out.status()).isEqualTo("DISPATCHED");
        assertThat(out.actualDispatchedAt()).isNotNull();
    }

    // ─── Tenant isolation ────────────────────────────────────────────────

    @Test
    void cross_tenant_run_access_throws_not_found() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(RUN_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("not found for tenant");
        assertThatThrownBy(() -> service.dispatchInject(RUN_ID, 1L))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.transition(RUN_ID, "PAUSE", null))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── Timeline ───────────────────────────────────────────────────────

    @Test
    void timeline_returns_entries_in_chronological_order_with_custom_injects() {
        ExerciseRun run = runFixture("COMPLETED");
        run.setStartedAt(LocalDateTime.of(2026, 5, 1, 9, 0));
        run.setEndedAt(LocalDateTime.of(2026, 5, 1, 12, 0));
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));

        // Two source injects (offset 0 + 30) plus one custom inject (no source).
        ExerciseRunInject i1 = runInjectFixture(1L, RUN_ID, "ACKNOWLEDGED", 100L);
        i1.setPlannedTimeOffsetMinutes(0);
        i1.setActualDispatchedAt(LocalDateTime.of(2026, 5, 1, 9, 0));
        ExerciseRunInject i2 = runInjectFixture(2L, RUN_ID, "DISPATCHED", 101L);
        i2.setPlannedTimeOffsetMinutes(30);
        i2.setActualDispatchedAt(LocalDateTime.of(2026, 5, 1, 9, 32));
        ExerciseRunInject custom = runInjectFixture(3L, RUN_ID, "ACKNOWLEDGED", null);
        custom.setSourceInjectId(null);
        custom.setPlannedTimeOffsetMinutes(15);
        custom.setActualDispatchedAt(LocalDateTime.of(2026, 5, 1, 9, 17));

        when(runInjectRepo.findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(ORG_ID, RUN_ID))
            .thenReturn(List.of(i1, custom, i2));

        ExerciseRunResponse r1 = new ExerciseRunResponse();
        r1.setId(11L); r1.setRunInjectId(1L); r1.setParticipantUserId(7L);
        r1.setResponseAt(LocalDateTime.of(2026, 5, 1, 9, 1)); r1.setResponseTimeSeconds(60);

        ExerciseRunResponse r2 = new ExerciseRunResponse();
        r2.setId(12L); r2.setRunInjectId(3L); r2.setParticipantUserId(8L);
        r2.setResponseAt(LocalDateTime.of(2026, 5, 1, 9, 20)); r2.setResponseTimeSeconds(180);

        when(responseRepo.findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(ORG_ID, 1L))
            .thenReturn(List.of(r1));
        when(responseRepo.findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(ORG_ID, 3L))
            .thenReturn(List.of(r2));
        when(responseRepo.findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(ORG_ID, 2L))
            .thenReturn(List.of());

        RunTimeline tl = service.getRunTimeline(RUN_ID);

        assertThat(tl.runId()).isEqualTo(RUN_ID);
        assertThat(tl.status()).isEqualTo("COMPLETED");

        // The custom inject must appear interleaved (at 09:17) between i1
        // (and its response at 09:01) and i2 (at 09:32).
        List<TimelineEntry> entries = tl.entries();
        assertThat(entries).isNotEmpty();
        assertThat(entries).extracting(TimelineEntry::at)
            .isSortedAccordingTo(java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));

        long customCount = entries.stream().filter(e -> "CUSTOM_INJECT".equals(e.kind())).count();
        long dispatchedCount = entries.stream().filter(e -> "INJECT_DISPATCHED".equals(e.kind())).count();
        long responseCount = entries.stream().filter(e -> "RESPONSE".equals(e.kind())).count();
        long stateChangeCount = entries.stream().filter(e -> "STATE_CHANGE".equals(e.kind())).count();
        assertThat(customCount).isEqualTo(1);
        assertThat(dispatchedCount).isEqualTo(2);
        assertThat(responseCount).isEqualTo(2);
        assertThat(stateChangeCount).isEqualTo(1);   // ended_at
    }

    // ─── RunDetail composition ──────────────────────────────────────────

    @Test
    void get_returns_run_detail_with_inject_queue() {
        ExerciseRun run = runFixture("IN_PROGRESS");
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runInjectRepo.findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(ORG_ID, RUN_ID))
            .thenReturn(List.of(
                runInjectFixture(1L, RUN_ID, "PENDING", 100L),
                runInjectFixture(2L, RUN_ID, "DISPATCHED", 101L)));

        RunDetail d = service.get(RUN_ID);

        assertThat(d.summary().id()).isEqualTo(RUN_ID);
        assertThat(d.injects()).hasSize(2);
        assertThat(d.injects().get(1).status()).isEqualTo("DISPATCHED");
    }
}
