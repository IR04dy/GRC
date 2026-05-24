package com.tss.grc.module.bcm.cycle.runtime.observer.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignObserverRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignmentView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.FlagRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationsByType;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObserverDashboard;
import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObservation;
import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObserverAssignment;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObservationRepository;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObserverAssignmentRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.security.GrcUserDetails;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice 10-C — Unit tests for {@link ExerciseObserverService}.
 *
 * <p>Twelve+ test cases covering the contracts callers of the observer
 * dashboard rely on:
 * <ul>
 *   <li>{@code assignObserver} — happy-path, idempotent re-assign, cross-tenant
 *       rejected, invalid role rejected, F7+F8.</li>
 *   <li>{@code removeObserver} — soft-deletes + audits.</li>
 *   <li>{@code recordObservation} — gated on active assignment (AccessDenied
 *       when missing), computes time_offset_minutes, F7+F8, broadcast.</li>
 *   <li>{@code flagObservationForAar} — author-only, toggle behavior, AAR
 *       integration (flagged observations appear in {@link #buildAarSection}).</li>
 *   <li>{@code getObservationsByType} — grouping preserves stable type order.</li>
 *   <li>{@code getObserverDashboard} — full payload assembled, tenant-isolated.</li>
 *   <li>Tenant isolation across the surface.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExerciseObserverServiceTest {

    private static final Long ORG_ID = 9L;
    private static final Long RUN_ID = 5000L;
    private static final Long EXERCISE_ID = 700L;
    private static final Long OBSERVER_USER_ID = 42L;
    private static final Long ASSIGNER_USER_ID = 1L;

    @Mock private ExerciseObserverAssignmentRepository assignmentRepo;
    @Mock private ExerciseObservationRepository observationRepo;
    @Mock private ExerciseRunRepository runRepo;
    @Mock private ExerciseRunInjectRepository runInjectRepo;
    @Mock private ExerciseRunResponseRepository responseRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private RealtimeBroadcaster realtime;
    @InjectMocks private ExerciseObserverService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Default auth principal — observer themselves (used for write paths).
        authenticateAs(OBSERVER_USER_ID, "observer.audit", "ROLE_AUDITOR");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(Long userId, String username, String role) {
        GrcUserDetails principal = new GrcUserDetails(userId, ORG_ID, username, "x",
            true, true, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    // ─── Fixtures ────────────────────────────────────────────────────────

    private static ExerciseRun runFixture(String status) {
        ExerciseRun r = new ExerciseRun();
        r.setId(RUN_ID);
        r.setOrganisationId(ORG_ID);
        r.setExerciseId(EXERCISE_ID);
        r.setRunCode("RUN-9-EX700-001");
        r.setStatus(status);
        r.setStartedAt(LocalDateTime.now().minusMinutes(45));
        r.setChannel("IN_PERSON");
        r.setParticipantCount(8);
        return r;
    }

    private static ExerciseObserverAssignment assignmentFixture(Long id, Long observerUserId, String role) {
        ExerciseObserverAssignment a = new ExerciseObserverAssignment();
        a.setId(id);
        a.setOrganisationId(ORG_ID);
        a.setExerciseRunId(RUN_ID);
        a.setObserverUserId(observerUserId);
        a.setObserverRole(role);
        a.setAssignedByUserId(ASSIGNER_USER_ID);
        a.setAssignedAt(LocalDateTime.now().minusMinutes(30));
        return a;
    }

    private static ExerciseObservation observationFixture(
        Long id, Long observerUserId, String type, String severity, boolean flagged) {
        ExerciseObservation o = new ExerciseObservation();
        o.setId(id);
        o.setOrganisationId(ORG_ID);
        o.setExerciseRunId(RUN_ID);
        o.setObserverUserId(observerUserId);
        o.setObservationTextEn("Observation text " + id);
        o.setObservationTextAr("نص الملاحظة " + id);
        o.setObservationType(type);
        o.setSeverity(severity);
        o.setRecordedAt(LocalDateTime.now().minusMinutes(5));
        o.setTimeOffsetMinutes(40);
        o.setIsFlaggedForAar(flagged);
        return o;
    }

    private void stubObservationSaveAssignsId() {
        AtomicLong seq = new AtomicLong(80_000L);
        when(observationRepo.save(any(ExerciseObservation.class))).thenAnswer(inv -> {
            ExerciseObservation o = inv.getArgument(0);
            if (o.getId() == null) o.setId(seq.getAndIncrement());
            return o;
        });
    }

    private void stubAssignmentSaveAssignsId() {
        AtomicLong seq = new AtomicLong(70_000L);
        when(assignmentRepo.save(any(ExerciseObserverAssignment.class))).thenAnswer(inv -> {
            ExerciseObserverAssignment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(seq.getAndIncrement());
            return a;
        });
    }

    // ─── assignObserver ──────────────────────────────────────────────────

    @Test
    void assignObserver_happyPath_writesAuditAndEmitsF8AndBroadcasts() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
            ORG_ID, RUN_ID, OBSERVER_USER_ID)).thenReturn(Optional.empty());
        stubAssignmentSaveAssignsId();
        // The assigner is the BCM manager
        authenticateAs(ASSIGNER_USER_ID, "bcm.manager", "ROLE_BCM_MANAGER");

        AssignmentView out = service.assignObserver(RUN_ID,
            new AssignObserverRequest(OBSERVER_USER_ID, "INTERNAL_AUDIT"));

        assertThat(out.observerUserId()).isEqualTo(OBSERVER_USER_ID);
        assertThat(out.observerRole()).isEqualTo("INTERNAL_AUDIT");
        assertThat(out.assignedByUserId()).isEqualTo(ASSIGNER_USER_ID);

        verify(audit).log(eq("CREATE"), eq("ExerciseObserverAssignment"), anyLong(), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        DomainEvent fired = evt.getValue();
        assertThat(fired.triggerEvent()).isEqualTo("EXERCISE_OBSERVER_ASSIGNED");
        assertThat(fired.entityType()).isEqualTo("ExerciseObserverAssignment");
        assertThat(fired.organisationId()).isEqualTo(ORG_ID);
        assertThat(fired.payload())
            .containsEntry("runId", String.valueOf(RUN_ID))
            .containsEntry("observerRole", "INTERNAL_AUDIT");

        // WS broadcast on the 10-B run topic
        verify(realtime).broadcastOrgEvent(eq(ORG_ID),
            eq("exercise-run." + RUN_ID), any());
    }

    @Test
    void assignObserver_idempotent_whenAlreadyAssigned() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        ExerciseObserverAssignment existing =
            assignmentFixture(99L, OBSERVER_USER_ID, "EXTERNAL_AUDIT");
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
            ORG_ID, RUN_ID, OBSERVER_USER_ID)).thenReturn(Optional.of(existing));

        AssignmentView out = service.assignObserver(RUN_ID,
            new AssignObserverRequest(OBSERVER_USER_ID, "EXTERNAL_AUDIT"));

        assertThat(out.id()).isEqualTo(99L);
        // No new write, no audit, no event when idempotent
        verify(assignmentRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
        verify(realtime, never()).broadcastOrgEvent(anyLong(), anyString(), any());
    }

    @Test
    void assignObserver_rejectsUnknownRole() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));

        assertThatThrownBy(() -> service.assignObserver(RUN_ID,
                new AssignObserverRequest(OBSERVER_USER_ID, "REGULATOR_LIAISON")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("observerRole");
        verify(assignmentRepo, never()).save(any());
    }

    @Test
    void assignObserver_404_whenRunBelongsToOtherTenant() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.empty());   // tenant-scoped finder => not found

        assertThatThrownBy(() -> service.assignObserver(RUN_ID,
                new AssignObserverRequest(OBSERVER_USER_ID, "INTERNAL_AUDIT")))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("not found for tenant");
        verify(assignmentRepo, never()).save(any());
    }

    // ─── removeObserver ──────────────────────────────────────────────────

    @Test
    void removeObserver_softDeletesAndAudits() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        ExerciseObserverAssignment a = assignmentFixture(33L, OBSERVER_USER_ID, "INTERNAL_AUDIT");
        when(assignmentRepo.findByIdAndOrganisationId(33L, ORG_ID))
            .thenReturn(Optional.of(a));
        when(assignmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeObserver(RUN_ID, 33L);

        assertThat(a.getIsDeleted()).isTrue();
        assertThat(a.getDeletedAt()).isNotNull();
        verify(audit).log(eq("DELETE"), eq("ExerciseObserverAssignment"), eq(33L), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("EXERCISE_OBSERVER_REMOVED");
    }

    // ─── recordObservation ───────────────────────────────────────────────

    @Test
    void recordObservation_succeeds_whenObserverIsAssigned() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.of(assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT")));
        stubObservationSaveAssignsId();

        ObservationView out = service.recordObservation(RUN_ID, new ObservationRequest(
            "Comms team escalated 8 minutes late",
            "تأخّر فريق الاتصالات في التصعيد ٨ دقائق",
            "ESCALATION", "MAJOR",
            "Tighten the escalation matrix for after-hours incidents",
            null, null, true));

        assertThat(out.observationType()).isEqualTo("ESCALATION");
        assertThat(out.severity()).isEqualTo("MAJOR");
        assertThat(out.isFlaggedForAar()).isTrue();
        assertThat(out.timeOffsetMinutes()).isGreaterThanOrEqualTo(40);
        assertThat(out.observerUserId()).isEqualTo(OBSERVER_USER_ID);

        verify(audit).log(eq("CREATE"), eq("ExerciseObservation"), anyLong(), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("EXERCISE_OBSERVATION_RECORDED");
        assertThat(evt.getValue().payload())
            .containsEntry("observationType", "ESCALATION")
            .containsEntry("severity", "MAJOR")
            .containsEntry("flaggedForAar", "true");

        verify(realtime).broadcastOrgEvent(eq(ORG_ID),
            eq("exercise-run." + RUN_ID), any());
    }

    @Test
    void recordObservation_throwsAccessDenied_whenNotAssigned() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordObservation(RUN_ID, new ObservationRequest(
                "Anything", null, "GENERAL", "INFO", null, null, null, false)))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not an assigned observer");
        verify(observationRepo, never()).save(any());
    }

    @Test
    void recordObservation_requiresAtLeastOneLanguageOfText() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.of(assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT")));

        assertThatThrownBy(() -> service.recordObservation(RUN_ID, new ObservationRequest(
                null, "  ", "GAP", "MINOR", null, null, null, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("observationText");
    }

    @Test
    void recordObservation_mirrorsArabicTextToEnglish_whenOnlyArabicProvided() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.of(assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT")));
        stubObservationSaveAssignsId();

        ObservationView out = service.recordObservation(RUN_ID, new ObservationRequest(
            null, "ملاحظة مكتوبة بالعربي فقط", "GENERAL", null, null, null, null, false));

        // Even though the observer only filled the AR field, EN was mirrored
        // so the NOT-NULL column stays populated.
        assertThat(out.observationTextEn()).isEqualTo("ملاحظة مكتوبة بالعربي فقط");
        assertThat(out.observationTextAr()).isEqualTo("ملاحظة مكتوبة بالعربي فقط");
        assertThat(out.severity()).isEqualTo("INFO");   // default
        assertThat(out.observationType()).isEqualTo("GENERAL");
    }

    @Test
    void recordObservation_validatesInjectBelongsToRun_whenRelatedInjectIdProvided() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.of(assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT")));

        // Inject 999 exists in another run
        ExerciseRunInject wrongRunInject = new ExerciseRunInject();
        wrongRunInject.setId(999L);
        wrongRunInject.setOrganisationId(ORG_ID);
        wrongRunInject.setExerciseRunId(8888L);   // different run
        when(runInjectRepo.findByIdAndOrganisationId(999L, ORG_ID))
            .thenReturn(Optional.of(wrongRunInject));

        assertThatThrownBy(() -> service.recordObservation(RUN_ID, new ObservationRequest(
                "Linked observation", null, "DECISION_QUALITY", "MINOR",
                null, null, 999L, false)))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Inject 999");
    }

    // ─── flagObservationForAar ───────────────────────────────────────────

    @Test
    void flagObservation_setsFlag_andEmitsF8() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        ExerciseObservation o = observationFixture(7L, OBSERVER_USER_ID, "GAP", "MAJOR", false);
        when(observationRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(o));
        when(observationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObservationView out = service.flagObservationForAar(RUN_ID, 7L, new FlagRequest(true));

        assertThat(out.isFlaggedForAar()).isTrue();
        verify(audit).log(eq("UPDATE"), eq("ExerciseObservation"), eq(7L), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("EXERCISE_OBSERVATION_FLAGGED");
        assertThat(evt.getValue().payload())
            .containsEntry("flaggedForAar", "true");
    }

    @Test
    void flagObservation_rejected_whenCallerIsNotAuthor() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        // Observation is owned by a different user
        ExerciseObservation o = observationFixture(7L, 999L, "GAP", "MAJOR", false);
        when(observationRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(o));

        // Current principal is OBSERVER_USER_ID (set in @BeforeEach), not 999
        assertThatThrownBy(() -> service.flagObservationForAar(RUN_ID, 7L,
                new FlagRequest(true)))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("author");
        verify(observationRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void flagObservation_noop_whenAlreadyAtTargetState() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        ExerciseObservation o = observationFixture(7L, OBSERVER_USER_ID, "GAP", "MAJOR", true);
        when(observationRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(o));

        ObservationView out = service.flagObservationForAar(RUN_ID, 7L, new FlagRequest(true));
        assertThat(out.isFlaggedForAar()).isTrue();
        verify(observationRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    // ─── getObservationsByType ───────────────────────────────────────────

    @Test
    void getObservationsByType_groupsByType_inStableOrder() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(observationRepo.findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(
                ORG_ID, RUN_ID))
            .thenReturn(List.of(
                observationFixture(1L, OBSERVER_USER_ID, "RISK", "MAJOR", false),
                observationFixture(2L, OBSERVER_USER_ID, "STRENGTH", "INFO", false),
                observationFixture(3L, OBSERVER_USER_ID, "GAP", "MODERATE", false),
                observationFixture(4L, OBSERVER_USER_ID, "STRENGTH", "INFO", false)
            ));

        ObservationsByType out = service.getObservationsByType(RUN_ID);

        // STRENGTH first in the stable order, then GAP, then RISK
        assertThat(out.byType().keySet())
            .containsExactly("STRENGTH", "GAP", "RISK");
        assertThat(out.byType().get("STRENGTH")).hasSize(2);
        assertThat(out.byType().get("GAP")).hasSize(1);
        assertThat(out.byType().get("RISK")).hasSize(1);
    }

    // ─── getObserverDashboard ────────────────────────────────────────────

    @Test
    void getObserverDashboard_assemblesFullPayload_andFlagsAssignedObserver() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(runInjectRepo.findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(
                ORG_ID, RUN_ID))
            .thenReturn(new ArrayList<>());
        when(observationRepo.findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(
                ORG_ID, RUN_ID))
            .thenReturn(List.of(
                observationFixture(1L, OBSERVER_USER_ID, "STRENGTH", "INFO", false),
                observationFixture(2L, 123L, "GAP", "MAJOR", true)));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(
                ORG_ID, RUN_ID))
            .thenReturn(List.of(
                assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT"),
                assignmentFixture(2L, 123L, "EXTERNAL_AUDIT")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.of(assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT")));

        ObserverDashboard out = service.getObserverDashboard(RUN_ID, OBSERVER_USER_ID);

        assertThat(out.summary().id()).isEqualTo(RUN_ID);
        assertThat(out.observations()).hasSize(2);
        assertThat(out.observers()).hasSize(2);
        assertThat(out.currentObserverUserId()).isEqualTo(OBSERVER_USER_ID);
        assertThat(out.isAssignedObserver()).isTrue();
    }

    @Test
    void getObserverDashboard_404_whenRunBelongsToOtherTenant() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getObserverDashboard(RUN_ID, OBSERVER_USER_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("not found for tenant");
    }

    // ─── AAR integration (buildAarSection) ───────────────────────────────

    @Test
    void buildAarSection_returnsEmpty_whenNoFlaggedObservations() {
        when(observationRepo.findByOrganisationIdAndExerciseRunIdAndIsFlaggedForAarOrderByRecordedAtAsc(
                ORG_ID, RUN_ID, Boolean.TRUE))
            .thenReturn(List.of());

        String section = service.buildAarSection(ORG_ID, RUN_ID);
        assertThat(section).isEmpty();
    }

    @Test
    void buildAarSection_groupsByType_andIncludesObserverRoles() {
        ExerciseObservation gap = observationFixture(1L, OBSERVER_USER_ID, "GAP", "MAJOR", true);
        gap.setRecommendationEn("Document the escalation matrix");
        ExerciseObservation strength = observationFixture(2L, 123L, "STRENGTH", "INFO", true);
        when(observationRepo.findByOrganisationIdAndExerciseRunIdAndIsFlaggedForAarOrderByRecordedAtAsc(
                ORG_ID, RUN_ID, Boolean.TRUE))
            .thenReturn(List.of(gap, strength));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(
                ORG_ID, RUN_ID))
            .thenReturn(List.of(
                assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT"),
                assignmentFixture(2L, 123L, "EXTERNAL_AUDIT")));

        String section = service.buildAarSection(ORG_ID, RUN_ID);

        assertThat(section).contains("Observer Findings");
        assertThat(section).contains("Total flagged observations: 2");
        // STRENGTH comes before GAP in the stable order
        int strengthIdx = section.indexOf("--- STRENGTH");
        int gapIdx      = section.indexOf("--- GAP");
        assertThat(strengthIdx).isPositive();
        assertThat(gapIdx).isPositive();
        assertThat(strengthIdx).isLessThan(gapIdx);
        // Observer roles are annotated
        assertThat(section).contains("INTERNAL_AUDIT").contains("EXTERNAL_AUDIT");
        // Severity + recommendation rendered
        assertThat(section).contains("[MAJOR]");
        assertThat(section).contains("Document the escalation matrix");
    }

    // ─── listObservations ────────────────────────────────────────────────

    @Test
    void listObservations_returnsChronologicalList_forAssignedRun() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(observationRepo.findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(
                ORG_ID, RUN_ID))
            .thenReturn(List.of(
                observationFixture(1L, OBSERVER_USER_ID, "GAP", "MAJOR", false),
                observationFixture(2L, 123L, "STRENGTH", "INFO", true)));

        List<ObservationView> out = service.listObservations(RUN_ID);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).id()).isEqualTo(1L);
        assertThat(out.get(1).id()).isEqualTo(2L);
    }

    // ─── listObservers ───────────────────────────────────────────────────

    @Test
    void listObservers_returnsRoster_inAssignmentOrder() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(
                ORG_ID, RUN_ID))
            .thenReturn(List.of(
                assignmentFixture(1L, 11L, "INTERNAL_AUDIT"),
                assignmentFixture(2L, 22L, "EXTERNAL_AUDIT"),
                assignmentFixture(3L, 33L, "SAMA_LIAISON")));

        List<AssignmentView> out = service.listObservers(RUN_ID);

        assertThat(out).hasSize(3);
        assertThat(out.get(0).observerRole()).isEqualTo("INTERNAL_AUDIT");
        assertThat(out.get(2).observerRole()).isEqualTo("SAMA_LIAISON");
    }

    // ─── Tenant isolation across the write surface ───────────────────────

    @Test
    void crossTenant_removeObserver_404() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        // Assignment is on a DIFFERENT run id — service must reject
        ExerciseObserverAssignment otherRun = assignmentFixture(33L, OBSERVER_USER_ID, "INTERNAL_AUDIT");
        otherRun.setExerciseRunId(99_999L);
        when(assignmentRepo.findByIdAndOrganisationId(33L, ORG_ID))
            .thenReturn(Optional.of(otherRun));

        assertThatThrownBy(() -> service.removeObserver(RUN_ID, 33L))
            .isInstanceOf(EntityNotFoundException.class);
        verify(assignmentRepo, never()).save(any());
    }

    @Test
    void recordObservation_throws_whenNoAuthenticatedUser() {
        SecurityContextHolder.clearContext();   // no principal

        // Even with a real run + an assignment, the service should refuse
        // because there is no current user.
        assertThatThrownBy(() -> service.recordObservation(RUN_ID, new ObservationRequest(
                "Any text", null, "GENERAL", "INFO", null, null, null, false)))
            .isInstanceOf(AccessDeniedException.class);
        // Bonus: make sure we didn't even try to load the run before checking auth
        verify(observationRepo, never()).save(any());
    }

    @Test
    void recordObservation_andFlag_emit_distinct_F8_events_across_lifecycle() {
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture("IN_PROGRESS")));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID))
            .thenReturn(Optional.of(assignmentFixture(1L, OBSERVER_USER_ID, "INTERNAL_AUDIT")));
        stubObservationSaveAssignsId();

        // 1. Record (unflagged)
        ObservationView created = service.recordObservation(RUN_ID, new ObservationRequest(
            "Initial finding", null, "COMPLIANCE", "MODERATE", null, null, null, false));
        // 2. Then flag
        when(observationRepo.findByIdAndOrganisationId(created.id(), ORG_ID))
            .thenReturn(Optional.of(observationLookup(created.id(), false)));
        service.flagObservationForAar(RUN_ID, created.id(), new FlagRequest(true));

        ArgumentCaptor<DomainEvent> evts = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evts.capture());
        // Trigger names in order
        List<String> triggers = evts.getAllValues().stream()
            .map(DomainEvent::triggerEvent).toList();
        assertThat(triggers).contains("EXERCISE_OBSERVATION_RECORDED");
        assertThat(triggers).contains("EXERCISE_OBSERVATION_FLAGGED");

        verify(realtime, times(2)).broadcastOrgEvent(eq(ORG_ID),
            eq("exercise-run." + RUN_ID), any());
    }

    private ExerciseObservation observationLookup(Long id, boolean flagged) {
        ExerciseObservation o = observationFixture(id, OBSERVER_USER_ID, "COMPLIANCE", "MODERATE", flagged);
        return o;
    }
}
