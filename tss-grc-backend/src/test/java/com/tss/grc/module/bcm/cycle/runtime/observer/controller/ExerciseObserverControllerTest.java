package com.tss.grc.module.bcm.cycle.runtime.observer.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignObserverRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignmentView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.FlagRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObserverDashboard;
import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObservation;
import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObserverAssignment;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObservationRepository;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObserverAssignmentRepository;
import com.tss.grc.module.bcm.cycle.runtime.observer.service.ExerciseObserverService;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import com.tss.grc.module.bcm.cycle.service.BcmRemediationService;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice 10-C — Slice-style controller test for the observer REST surface.
 *
 * <p>Wires a real {@link ExerciseObserverService} against mocked repos +
 * audit + publisher + realtime, then drives the controller directly. Same
 * pattern as Slice 10-F's {@code BcpBinderControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExerciseObserverControllerTest {

    private static final Long ORG_ID = 88L;
    private static final Long RUN_ID = 5000L;
    private static final Long EXERCISE_ID = 700L;
    private static final Long OBSERVER_USER_ID = 42L;

    @Mock private ExerciseObserverAssignmentRepository assignmentRepo;
    @Mock private ExerciseObservationRepository observationRepo;
    @Mock private ExerciseRunRepository runRepo;
    @Mock private ExerciseRunInjectRepository runInjectRepo;
    @Mock private ExerciseRunResponseRepository responseRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private RealtimeBroadcaster realtime;
    @Mock private BcmRemediationService remediation;
    @Mock private BcmGapRepository gaps;

    private ExerciseObserverService service;
    private ExerciseObserverController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        GrcUserDetails principal = new GrcUserDetails(OBSERVER_USER_ID, ORG_ID,
            "auditor", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_AUDITOR")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));

        service = new ExerciseObserverService(
            assignmentRepo, observationRepo, runRepo, runInjectRepo, responseRepo,
            audit, publisher, realtime, remediation, gaps);
        controller = new ExerciseObserverController(service);

        // Standard run lookup — tenant-matched
        when(runRepo.findByIdAndOrganisationId(RUN_ID, ORG_ID))
            .thenReturn(Optional.of(runFixture()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private ExerciseRun runFixture() {
        ExerciseRun r = new ExerciseRun();
        r.setId(RUN_ID); r.setOrganisationId(ORG_ID); r.setExerciseId(EXERCISE_ID);
        r.setRunCode("RUN-88-EX700-001"); r.setStatus("IN_PROGRESS");
        r.setStartedAt(LocalDateTime.now().minusMinutes(30));
        r.setChannel("IN_PERSON"); r.setParticipantCount(6);
        return r;
    }

    // ─── Tests ───────────────────────────────────────────────────────────

    @Test
    void assignObserver_returnsCreatedAssignment() {
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID)).thenReturn(Optional.empty());
        AtomicLong seq = new AtomicLong(70_000L);
        when(assignmentRepo.save(any(ExerciseObserverAssignment.class))).thenAnswer(inv -> {
            ExerciseObserverAssignment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(seq.getAndIncrement());
            return a;
        });

        AssignmentView out = controller.assignObserver(RUN_ID,
            new AssignObserverRequest(OBSERVER_USER_ID, "INTERNAL_AUDIT"));

        assertThat(out.observerUserId()).isEqualTo(OBSERVER_USER_ID);
        assertThat(out.observerRole()).isEqualTo("INTERNAL_AUDIT");
        verify(assignmentRepo).save(any());
    }

    @Test
    void removeObserver_returns204NoContent() {
        ExerciseObserverAssignment a = new ExerciseObserverAssignment();
        a.setId(33L); a.setOrganisationId(ORG_ID); a.setExerciseRunId(RUN_ID);
        a.setObserverUserId(OBSERVER_USER_ID); a.setObserverRole("INTERNAL_AUDIT");
        a.setAssignedAt(LocalDateTime.now().minusMinutes(10));
        when(assignmentRepo.findByIdAndOrganisationId(33L, ORG_ID)).thenReturn(Optional.of(a));
        when(assignmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Void> resp = controller.removeObserver(RUN_ID, 33L);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        assertThat(a.getIsDeleted()).isTrue();
    }

    @Test
    void getObserverDashboard_returnsFullPayload() {
        // Observer is assigned on this run
        ExerciseObserverAssignment a = new ExerciseObserverAssignment();
        a.setId(1L); a.setOrganisationId(ORG_ID); a.setExerciseRunId(RUN_ID);
        a.setObserverUserId(OBSERVER_USER_ID); a.setObserverRole("INTERNAL_AUDIT");
        a.setAssignedAt(LocalDateTime.now().minusMinutes(10));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(
                ORG_ID, RUN_ID)).thenReturn(List.of(a));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID)).thenReturn(Optional.of(a));
        when(runInjectRepo.findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(
                ORG_ID, RUN_ID)).thenReturn(new ArrayList<>());
        when(observationRepo.findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(
                ORG_ID, RUN_ID)).thenReturn(new ArrayList<>());

        ObserverDashboard out = controller.getObserverDashboard(RUN_ID, null);

        assertThat(out.summary().id()).isEqualTo(RUN_ID);
        assertThat(out.observers()).hasSize(1);
        assertThat(out.isAssignedObserver()).isTrue();
        assertThat(out.currentObserverUserId()).isEqualTo(OBSERVER_USER_ID);
    }

    @Test
    void recordObservation_returnsCreatedObservation_withTimeOffset() {
        // Caller is the assigned observer
        ExerciseObserverAssignment a = new ExerciseObserverAssignment();
        a.setId(1L); a.setOrganisationId(ORG_ID); a.setExerciseRunId(RUN_ID);
        a.setObserverUserId(OBSERVER_USER_ID); a.setObserverRole("INTERNAL_AUDIT");
        a.setAssignedAt(LocalDateTime.now().minusMinutes(10));
        when(assignmentRepo.findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                ORG_ID, RUN_ID, OBSERVER_USER_ID)).thenReturn(Optional.of(a));
        AtomicLong seq = new AtomicLong(80_000L);
        when(observationRepo.save(any(ExerciseObservation.class))).thenAnswer(inv -> {
            ExerciseObservation o = inv.getArgument(0);
            if (o.getId() == null) o.setId(seq.getAndIncrement());
            return o;
        });

        ObservationView out = controller.recordObservation(RUN_ID, new ObservationRequest(
            "Comms team escalated 8 minutes late",
            null, "ESCALATION", "MAJOR",
            "Tighten the escalation matrix", null, null, true));

        assertThat(out.observationType()).isEqualTo("ESCALATION");
        assertThat(out.severity()).isEqualTo("MAJOR");
        assertThat(out.isFlaggedForAar()).isTrue();
        assertThat(out.timeOffsetMinutes()).isGreaterThanOrEqualTo(29);   // run started 30m ago
    }

    @Test
    void flagObservation_returnsUpdatedView_withFlagSet() {
        ExerciseObservation o = new ExerciseObservation();
        o.setId(7L); o.setOrganisationId(ORG_ID); o.setExerciseRunId(RUN_ID);
        o.setObserverUserId(OBSERVER_USER_ID);
        o.setObservationTextEn("x"); o.setObservationType("GAP"); o.setSeverity("MAJOR");
        o.setRecordedAt(LocalDateTime.now()); o.setTimeOffsetMinutes(20);
        o.setIsFlaggedForAar(false);
        when(observationRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(o));
        when(observationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ObservationView out = controller.flagObservation(RUN_ID, 7L, new FlagRequest(true));

        assertThat(out.isFlaggedForAar()).isTrue();
        verify(observationRepo).save(any());
    }

    @Test
    void listObservations_returnsList() {
        ExerciseObservation o1 = new ExerciseObservation();
        o1.setId(1L); o1.setOrganisationId(ORG_ID); o1.setExerciseRunId(RUN_ID);
        o1.setObserverUserId(OBSERVER_USER_ID);
        o1.setObservationTextEn("first"); o1.setObservationType("GAP"); o1.setSeverity("MAJOR");
        o1.setRecordedAt(LocalDateTime.now().minusMinutes(5)); o1.setTimeOffsetMinutes(25);
        o1.setIsFlaggedForAar(false);

        when(observationRepo.findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(
                ORG_ID, RUN_ID)).thenReturn(List.of(o1));

        List<ObservationView> out = controller.listObservations(RUN_ID);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).id()).isEqualTo(1L);
    }
}
