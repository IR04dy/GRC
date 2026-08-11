package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestEvent;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRecipientRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-189 — the workflow engine's SLA sweep performed a transition the engine's own table forbids.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@link WorkflowRequestService#TRANSITIONS} is the declared lifecycle of a workflow request:
 * {@code transition()} answers {@code IllegalTransitionException} → <b>409</b> for anything outside it,
 * and it is what a reader, the frontend and {@code WorkflowEvidenceBridge} consult to know which states
 * are reachable. It declared {@code REOPENED → {IN_PROGRESS, SUBMITTED, ESCALATED, CLOSED}} — <b>no
 * OVERDUE</b>.
 *
 * <p>{@link WorkflowRequestSlaScheduler} kept its own {@code OVERDUE_SCAN_STATUSES}, which includes
 * {@code REOPENED}, and wrote {@code r.setStatus("OVERDUE")} directly. So a reopened request past its
 * due date was flipped <b>on a schedule</b> into a state the table says it cannot reach, while the
 * identical move through the API was refused with a 409. Two lists that had to agree, and nothing
 * compared them — in the engine {@code CLAUDE.md} records as adopted by <b>14 modules</b>.
 *
 * <p>A second, quieter one in the same method: when a multi-tier ladder climbed a rung on a request
 * already in OVERDUE, the code re-set {@code OVERDUE}. The table declares no {@code OVERDUE → OVERDUE}
 * self-loop either, so every tier climb was also an undeclared transition. Advancing the tier never
 * needed a status write.
 *
 * <h3>Which side was wrong</h3>
 *
 * <p>The sweep. A reopened request with a due date in the past <em>is</em> overdue — that is the whole
 * point of an SLA sweep, and refusing to escalate it would be the worse bug. So the table was completed
 * rather than the behaviour changed, and the sweep now asks {@link WorkflowRequestService#mayTransition}
 * instead of holding a second opinion. That is the F2-178 shape: one predicate, read by both.
 *
 * <h3>Why the list-vs-table assertion is the load-bearing test here</h3>
 *
 * <p>Completing the table makes the runtime skip branch unreachable today, which by F2-183's rule makes
 * it untested code unless something exercises it — {@link #aRequestInAStateTheLifecycleForbidsIsNotEscalated()}
 * hands the method such a row directly. But the assertion that stops the defect coming back is
 * {@link #everyStatusTheSweepScansMayBecomeOverdue()}: it compares the two lists that diverged, which is
 * the thing nobody was doing.
 */
class WorkflowSlaTransitionLegalityTest {

    @SuppressWarnings("unchecked")
    private static List<String> scanStatuses() throws Exception {
        Field f = WorkflowRequestSlaScheduler.class.getDeclaredField("OVERDUE_SCAN_STATUSES");
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    // ── the invariant that would have caught it ──────────────────────────────────────────────────

    @Test
    @DisplayName("every status the overdue sweep scans may legally become OVERDUE")
    void everyStatusTheSweepScansMayBecomeOverdue() throws Exception {
        List<String> scan = scanStatuses();
        Set<String> illegal = new TreeSet<>();
        for (String from : scan) {
            // OVERDUE is in the scan list on purpose (WF-10 re-scans so a ladder can keep climbing);
            // the sweep no longer writes the status for those rows, so it needs no self-loop.
            if ("OVERDUE".equals(from)) {
                continue;
            }
            if (!WorkflowRequestService.mayTransition(from, "OVERDUE")) {
                illegal.add(from);
            }
        }
        System.out.println("[F2-189] overdue scan statuses = " + scan + " ; illegal -> OVERDUE = " + illegal);
        assertThat(illegal)
            .as("""
                The SLA sweep scans these statuses for overdue requests, and WorkflowRequestService\
                .TRANSITIONS does not allow them to become OVERDUE. Whichever way it is resolved there \
                must be ONE answer: either the state genuinely may not become overdue (remove it from \
                OVERDUE_SCAN_STATUSES) or it may (add OVERDUE to its TRANSITIONS entry). What is not \
                defensible is the sweep writing a status the table refuses — that is REOPENED before \
                F2-189, flipped on a schedule while the API answered 409 for the same move.""")
            .isEmpty();
    }

    /**
     * The specific entry the defect was about, pinned by name so a future tidy-up of the table cannot
     * silently remove it and re-open the divergence from the other side.
     */
    @Test
    @DisplayName("REOPENED may become OVERDUE — the entry F2-189 added")
    void reopenedMayBecomeOverdue() {
        assertThat(WorkflowRequestService.mayTransition("REOPENED", "OVERDUE")).isTrue();
        assertThat(WorkflowRequestService.TRANSITIONS.get("REOPENED"))
            .as("the rest of REOPENED's lifecycle must be unchanged — this slice added one edge, it "
                + "did not redesign the state machine")
            .containsExactlyInAnyOrder("IN_PROGRESS", "SUBMITTED", "ESCALATED", "OVERDUE", "CLOSED");
    }

    /**
     * The self-loop stays absent, and that is a decision rather than an omission: the sweep no longer
     * writes the status for an already-OVERDUE row, so declaring {@code OVERDUE → OVERDUE} would only
     * make a bypass look legal.
     */
    @Test
    @DisplayName("OVERDUE has no self-loop, because nothing needs one any more")
    void overdueHasNoSelfLoop() {
        assertThat(WorkflowRequestService.mayTransition("OVERDUE", "OVERDUE"))
            .as("a tier climb advances escalationLevel; it must not re-write the status")
            .isFalse();
    }

    // ── the table is still a table ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("mayTransition and the table agree, and neither is vacuous")
    void thePredicateReadsTheTable() {
        Map<String, Set<String>> t = WorkflowRequestService.TRANSITIONS;
        assertThat(t)
            .as("the lifecycle table resolved almost nothing — every assertion above passes over it")
            .hasSizeGreaterThanOrEqualTo(15);
        assertThat(WorkflowRequestService.mayTransition("DRAFT", "SENT")).isTrue();
        assertThat(WorkflowRequestService.mayTransition("DRAFT", "APPROVED")).isFalse();
        assertThat(WorkflowRequestService.mayTransition("ARCHIVED", "SENT"))
            .as("ARCHIVED is terminal")
            .isFalse();
        assertThat(WorkflowRequestService.mayTransition("NO_SUCH_STATUS", "CLOSED"))
            .as("an unknown source state must not be treated as permissive")
            .isFalse();
        // Every declared target must itself be a declared state, or the table describes a lifecycle
        // that cannot be walked — the shape that let REOPENED -> OVERDUE be missing unnoticed.
        Set<String> dangling = new TreeSet<>();
        t.values().forEach(targets -> targets.forEach(to -> {
            if (!t.containsKey(to)) {
                dangling.add(to);
            }
        }));
        assertThat(dangling)
            .as("status(es) reachable per the table but with no entry of their own")
            .isEmpty();
    }

    // ── the runtime half, exercised rather than assumed ──────────────────────────────────────────

    private WorkflowRequestSlaScheduler scheduler(WorkflowRequestRepository requestRepo,
                                                  WorkflowRequestEventRepository eventRepo) {
        return new WorkflowRequestSlaScheduler(
            requestRepo,
            org.mockito.Mockito.mock(WorkflowRequestRecipientRepository.class),
            eventRepo,
            org.mockito.Mockito.mock(com.tss.grc.module.iam.repository.UserRepository.class),
            org.mockito.Mockito.mock(
                com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository.class),
            org.mockito.Mockito.mock(com.tss.grc.module.platform.service.NotificationService.class),
            org.mockito.Mockito.mock(
                com.tss.grc.module.platform.auditlog.service.AuditLogService.class),
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private static WorkflowRequest pastDue(String status) {
        WorkflowRequest r = new WorkflowRequest();
        r.setId(4242L);
        r.setOrganisationId(1L);
        r.setSourceModule("EVIDENCE");
        r.setRequestType("EVIDENCE_REQUEST");
        r.setTitleEn("Quarterly access recertification evidence");
        r.setStatus(status);
        r.setPriority("HIGH");
        r.setDueDate(java.time.LocalDate.of(2026, 1, 15));
        return r;
    }

    private boolean escalate(WorkflowRequestSlaScheduler s, WorkflowRequest r) throws Exception {
        java.lang.reflect.Method m = WorkflowRequestSlaScheduler.class.getDeclaredMethod(
            "escalateOverdue", WorkflowRequest.class, java.time.LocalDate.class,
            java.time.LocalDateTime.class);
        m.setAccessible(true);
        return (boolean) m.invoke(s, r, java.time.LocalDate.of(2026, 8, 11),
            java.time.LocalDateTime.of(2026, 8, 11, 9, 0));
    }

    /**
     * The branch completing the table made unreachable through the scan list. F2-183's rule: a branch
     * that is a no-op everywhere you can measure is untested code until you build it something to do —
     * so this hands the method a state the lifecycle genuinely forbids ({@code APPROVED}, which cannot
     * become OVERDUE and never will) and asserts it is left alone rather than flipped.
     */
    @Test
    @DisplayName("a request in a state the lifecycle forbids is not escalated")
    void aRequestInAStateTheLifecycleForbidsIsNotEscalated() throws Exception {
        WorkflowRequestRepository requestRepo = org.mockito.Mockito.mock(WorkflowRequestRepository.class);
        WorkflowRequestEventRepository eventRepo =
            org.mockito.Mockito.mock(WorkflowRequestEventRepository.class);
        WorkflowRequest r = pastDue("APPROVED");

        assertThat(WorkflowRequestService.mayTransition("APPROVED", "OVERDUE"))
            .as("fixture precondition: APPROVED must be a state that genuinely cannot become OVERDUE")
            .isFalse();
        assertThat(escalate(scheduler(requestRepo, eventRepo), r)).isFalse();

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getEscalatedAt()).isNull();
        org.mockito.Mockito.verify(requestRepo, org.mockito.Mockito.never())
            .save(org.mockito.ArgumentMatchers.any(WorkflowRequest.class));
        org.mockito.Mockito.verify(eventRepo, org.mockito.Mockito.never())
            .save(org.mockito.ArgumentMatchers.any(WorkflowRequestEvent.class));
    }

    /** The case the defect was about: it must still escalate, because a reopened request IS overdue. */
    @Test
    @DisplayName("a REOPENED past-due request is escalated, and its event records the real from-status")
    void reopenedIsStillEscalated() throws Exception {
        WorkflowRequestRepository requestRepo = org.mockito.Mockito.mock(WorkflowRequestRepository.class);
        WorkflowRequestEventRepository eventRepo =
            org.mockito.Mockito.mock(WorkflowRequestEventRepository.class);
        WorkflowRequest r = pastDue("REOPENED");
        org.mockito.Mockito.when(requestRepo.save(org.mockito.ArgumentMatchers.any(WorkflowRequest.class)))
            .thenAnswer(i -> i.getArgument(0));

        assertThat(escalate(scheduler(requestRepo, eventRepo), r)).isTrue();

        assertThat(r.getStatus()).isEqualTo("OVERDUE");
        assertThat(r.getEscalatedAt()).isNotNull();
        org.mockito.ArgumentCaptor<WorkflowRequestEvent> ev =
            org.mockito.ArgumentCaptor.forClass(WorkflowRequestEvent.class);
        org.mockito.Mockito.verify(eventRepo, org.mockito.Mockito.atLeastOnce()).save(ev.capture());
        assertThat(ev.getAllValues().get(0).getFromStatus())
            .as("the audit event must name the state it came FROM — REOPENED, which is the transition "
                + "the table used to deny")
            .isEqualTo("REOPENED");
        assertThat(ev.getAllValues().get(0).getToStatus()).isEqualTo("OVERDUE");
    }

    /**
     * The self-write. An already-OVERDUE request reaching a new escalation tier must advance the tier
     * and not re-write the status — the transition the table declares no self-loop for.
     */
    @Test
    @DisplayName("a tier climb on an already-OVERDUE request advances the tier without a status write")
    void aTierClimbDoesNotRewriteTheStatus() throws Exception {
        WorkflowRequestRepository requestRepo = org.mockito.Mockito.mock(WorkflowRequestRepository.class);
        WorkflowRequestEventRepository eventRepo =
            org.mockito.Mockito.mock(WorkflowRequestEventRepository.class);
        WorkflowRequest r = pastDue("OVERDUE");
        r.setEscalationLevel(1);
        r.setEscalatedAt(java.time.LocalDateTime.of(2026, 2, 1, 9, 0));
        // Two tiers, both elapsed by 2026-08-11 against a 2026-01-15 due date → target level 2.
        r.setEscalationTiers("[{\"afterDays\":1,\"userId\":null},{\"afterDays\":7,\"userId\":null}]");
        org.mockito.Mockito.when(requestRepo.save(org.mockito.ArgumentMatchers.any(WorkflowRequest.class)))
            .thenAnswer(i -> i.getArgument(0));

        assertThat(escalate(scheduler(requestRepo, eventRepo), r)).isTrue();

        assertThat(r.getStatus()).isEqualTo("OVERDUE");
        assertThat(r.getEscalationLevel())
            .as("the ladder must still climb — this slice removed a redundant status write, not the "
                + "WF-10 tier behaviour")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("the sweep's scan list is still readable, or the invariant above is vacuous")
    void theScanListIsStillThere() throws Exception {
        assertThat(scanStatuses())
            .as("OVERDUE_SCAN_STATUSES was renamed or emptied — everyStatusTheSweepScansMayBecomeOverdue "
                + "would then pass over nothing, which is the reassuring direction")
            .hasSizeGreaterThanOrEqualTo(5)
            .contains("REOPENED", "OVERDUE");
    }
}
