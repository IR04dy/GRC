package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestEvent;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-190 — the evidence bridge was a <b>third</b> writer of workflow-request status, and it ignored the
 * lifecycle table too.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@link WorkflowEvidenceBridge}'s comment said <i>"Drive through the WF-1 machine: pre-submit states
 * hop via SUBMITTED first"</i>. It drove around it. Two hard-coded sets — {@code TERMINAL} (4 states) and
 * {@code PRE_SUBMIT} (7) — covered <b>11 of the table's 16</b>. For the five in neither
 * ({@code DRAFT}, {@code SUBMITTED}, {@code UNDER_REVIEW}, {@code RETURNED}, {@code RESUBMITTED}) it
 * wrote the outcome directly, and the table forbids two of those moves:
 *
 * <ul>
 *   <li><b>{@code DRAFT → APPROVED}</b> — a request nobody ever sent, marked approved by an evidence
 *       event. {@code DRAFT}'s only legal continuations are {@code SENT} and {@code CLOSED}.</li>
 *   <li><b>{@code RETURNED → APPROVED}</b> — {@code RETURNED} must go through {@code RESUBMITTED}
 *       first, which is exactly what "returned for changes" means. Also {@code RETURNED → RETURNED},
 *       an undeclared self-loop, on an evidence rejection.</li>
 * </ul>
 *
 * <p>And the hop was decorative: {@code setStatus("SUBMITTED")} immediately followed by
 * {@code setStatus(target)} collapses, so the row never rested in SUBMITTED and no event recorded it.
 * 🔑 <b>A control described in a comment, implemented as two consecutive writes, runs as neither.</b>
 *
 * <h3>Latent, measured before grading</h3>
 *
 * <p>2026-08-11, both real databases: <b>0</b> evidence requests anchored to {@code WORKFLOW_REQUEST},
 * <b>0</b> events authored by {@code evidence-bridge}, 0 requests in DRAFT or RETURNED. The bridge has
 * never run. Nothing was corrupted; what was wrong is that the engine's declared lifecycle was not the
 * authority on its own states, in the third of three writers.
 *
 * <h3>Why depth 2 and not a path search</h3>
 *
 * <p>{@code DRAFT → SENT → SUBMITTED → APPROVED} exists in the table. Walking it would have an evidence
 * approval <em>send</em> the request on the requester's behalf and then approve it, so refusing is the
 * right answer and the cap is a product decision. {@code hopVia} is capped and its candidate order is
 * declared rather than incidental — F2-186 is a live finding about an answer that depended on
 * {@code HashMap} iteration order.
 */
class WorkflowEvidenceBridgeLegalityTest {

    private WorkflowRequestRepository requestRepo;
    private WorkflowRequestEventRepository eventRepo;
    private NotificationService notifications;
    private WorkflowEvidenceBridge bridge;

    @BeforeEach
    void setUp() {
        requestRepo   = mock(WorkflowRequestRepository.class);
        eventRepo     = mock(WorkflowRequestEventRepository.class);
        notifications = mock(NotificationService.class);
        bridge = new WorkflowEvidenceBridge(requestRepo, eventRepo, notifications,
            mock(AuditLogService.class));
        when(requestRepo.save(any(WorkflowRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private WorkflowRequest req(String status) {
        WorkflowRequest r = new WorkflowRequest();
        r.setId(9001L);
        r.setOrganisationId(1L);
        r.setSourceModule("EVIDENCE");
        r.setRequestType("EVIDENCE_REQUEST");
        r.setTitleEn("SOC 2 control evidence");
        r.setStatus(status);
        r.setInitiatedBy(7L);
        when(requestRepo.findByIdAndOrganisationId(9001L, 1L)).thenReturn(Optional.of(r));
        return r;
    }

    private void fire(boolean approved) {
        bridge.onEvidenceOutcome(DomainEvent.of(1L,
            approved ? "EVIDENCE_REQUEST_APPROVED" : "EVIDENCE_REQUEST_REJECTED",
            "EvidenceRequest", 55L,
            Map.of("anchorType", "WORKFLOW_REQUEST", "anchorId", 9001L, "requestCode", "EVR-0055")));
    }

    private List<WorkflowRequestEvent> events() {
        ArgumentCaptor<WorkflowRequestEvent> c = ArgumentCaptor.forClass(WorkflowRequestEvent.class);
        verify(eventRepo, org.mockito.Mockito.atLeast(0)).save(c.capture());
        return c.getAllValues();
    }

    // ── the two illegal moves ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a DRAFT request is not approved by an evidence outcome — it was never sent")
    void draftIsRefused() {
        WorkflowRequest r = req("DRAFT");

        fire(true);

        assertThat(r.getStatus())
            .as("DRAFT -> APPROVED is not in the lifecycle, and reaching APPROVED needs SENT — an "
                + "evidence approval must not send the request on the requester's behalf")
            .isEqualTo("DRAFT");
        verify(requestRepo, never()).save(any(WorkflowRequest.class));
        assertThat(events()).isEmpty();
        verify(notifications, never()).notifyUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a RETURNED request reaches APPROVED through RESUBMITTED, and both hops are recorded")
    void returnedHopsThroughResubmitted() {
        WorkflowRequest r = req("RETURNED");

        fire(true);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        List<WorkflowRequestEvent> evs = events();
        assertThat(evs)
            .as("two events: the lifecycle step the machine requires, then the decision. One event "
                + "claiming RETURNED -> APPROVED would describe a transition the table forbids")
            .hasSize(2);
        assertThat(evs.get(0).getFromStatus()).isEqualTo("RETURNED");
        assertThat(evs.get(0).getToStatus()).isEqualTo("RESUBMITTED");
        assertThat(evs.get(1).getFromStatus())
            .as("the deciding event must start where the hop left the row, not where the walk began")
            .isEqualTo("RESUBMITTED");
        assertThat(evs.get(1).getToStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("an evidence rejection on a RETURNED request does not self-loop")
    void returnedRejectionDoesNotSelfLoop() {
        WorkflowRequest r = req("RETURNED");

        fire(false);

        // RETURNED -> RETURNED is not declared; RETURNED -> RESUBMITTED -> RETURNED is.
        assertThat(r.getStatus()).isEqualTo("RETURNED");
        assertThat(events()).hasSize(2);
        assertThat(events().get(0).getToStatus()).isEqualTo("RESUBMITTED");
        assertThat(WorkflowRequestService.mayTransition("RETURNED", "RETURNED"))
            .as("the fixture's premise: the direct move is illegal, which is why a hop is needed")
            .isFalse();
    }

    // ── the ordinary path still works ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an open request still hops via SUBMITTED — the behaviour the old comment intended")
    void sentHopsViaSubmitted() {
        WorkflowRequest r = req("SENT");

        fire(true);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        List<WorkflowRequestEvent> evs = events();
        assertThat(evs).hasSize(2);
        assertThat(evs.get(0).getToStatus())
            .as("SUBMITTED is the preferred intermediate, and now it is actually persisted and "
                + "recorded rather than collapsed by the next write")
            .isEqualTo("SUBMITTED");
        assertThat(evs.get(1).getToStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("a request already under review is decided directly, with no invented hop")
    void underReviewIsDirect() {
        WorkflowRequest r = req("UNDER_REVIEW");

        fire(true);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(events())
            .as("UNDER_REVIEW -> APPROVED is legal, so there must be exactly one event")
            .hasSize(1);
        assertThat(events().get(0).getFromStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("an already-decided request is left alone")
    void terminalIsIgnored() {
        WorkflowRequest r = req("APPROVED");

        fire(false);

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        verify(requestRepo, never()).save(any(WorkflowRequest.class));
    }

    // ── the invariant, so the hard-coded sets cannot come back ───────────────────────────────────

    /**
     * The assertion that would have caught the original defect: for every state the bridge is willing
     * to act on, the outcome must be reachable in at most one hop. Before F2-190 this failed on
     * {@code DRAFT} (no route at all — correctly refused now) and {@code RETURNED} (route exists, and
     * the code took a forbidden shortcut instead).
     */
    @Test
    @DisplayName("every non-terminal state either reaches both outcomes in <=1 hop or is refused")
    void everyNonTerminalStateIsAccountedFor() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> terminal = (Set<String>) terminalSet();
        Set<String> unroutable = new TreeSet<>();
        for (String from : WorkflowRequestService.TRANSITIONS.keySet()) {
            if (terminal.contains(from)) {
                continue;
            }
            for (String target : List.of("APPROVED", "RETURNED")) {
                boolean direct = WorkflowRequestService.mayTransition(from, target);
                boolean hop = WorkflowRequestService.hopVia(from, target).isPresent();
                if (!direct && !hop) {
                    unroutable.add(from + " -> " + target);
                }
            }
        }
        System.out.println("[F2-190] non-terminal states with no <=1-hop route to an outcome = " + unroutable);
        assertThat(unroutable)
            .as("""
                These states cannot reach an evidence outcome within the cap, so the bridge refuses and \
                logs. That is the correct behaviour and this assertion is a record of WHICH states it \
                is, not a demand that the set be empty — DRAFT belongs here on purpose, because an \
                evidence approval must not send an unsent request. If a state joins or leaves this set, \
                somebody has changed what an evidence outcome can do to a workflow request.""")
            .containsExactly("DRAFT -> APPROVED", "DRAFT -> RETURNED");
    }

    @Test
    @DisplayName("the hard-coded PRE_SUBMIT list is gone, not merely unused")
    void thePreSubmitListIsGone() {
        assertThat(List.of(WorkflowEvidenceBridge.class.getDeclaredFields()))
            .as("PRE_SUBMIT was a second opinion about the lifecycle. Leaving it in place, even "
                + "unread, is how the next reader concludes it is the authority")
            .noneMatch(f -> f.getName().equals("PRE_SUBMIT"));
    }

    /**
     * {@code hopVia}'s contract, pinned because a caller that misreads it writes a bypass.
     *
     * <p>⚠ My first draft of this test asserted that {@code hopVia("SUBMITTED", "APPROVED")} is empty
     * "because the direct move exists" — the exact convenience the implementation deliberately does
     * <em>not</em> offer, and whose javadoc says so two lines above the method. It failed, correctly,
     * returning {@code UNDER_REVIEW}. 🔑 <b>A test written from a method's name instead of its
     * contract asserts the API you expected, not the one you have</b> — the mirror of writing a test
     * by reading the implementation, and it fails in the safer direction only by luck.
     *
     * <p>The contract: {@code hopVia} answers "which single intermediate makes this legal", full stop.
     * Deciding whether a hop is <em>needed</em> is the caller's job, which is why the bridge asks
     * {@link WorkflowRequestService#mayTransition} first. Collapsing the two answers into one
     * {@code Optional} is what would let a caller hop when it should have gone direct.
     */
    @Test
    @DisplayName("hopVia answers which intermediate is legal, and leaves 'is a hop needed' to the caller")
    void hopViaAnswersOnlyItsOwnQuestion() {
        // A direct move exists AND a two-hop route exists; hopVia reports the hop either way.
        assertThat(WorkflowRequestService.mayTransition("SUBMITTED", "APPROVED")).isTrue();
        assertThat(WorkflowRequestService.hopVia("SUBMITTED", "APPROVED"))
            .as("hopVia does not special-case a legal direct move — that is the caller's question, "
                + "and merging the two answers is how a needless hop gets written")
            .contains("UNDER_REVIEW");

        // No direct move: the hop is the whole answer, and SUBMITTED is the declared preference.
        assertThat(WorkflowRequestService.mayTransition("SENT", "APPROVED")).isFalse();
        assertThat(WorkflowRequestService.hopVia("SENT", "APPROVED")).contains("SUBMITTED");

        // Nothing outgoing at all.
        assertThat(WorkflowRequestService.hopVia("ARCHIVED", "APPROVED"))
            .as("a graph-terminal state has no edges, so there is no intermediate to offer")
            .isEmpty();
        assertThat(WorkflowRequestService.hopVia("DRAFT", "APPROVED"))
            .as("DRAFT has edges (SENT, CLOSED) and neither reaches APPROVED in one more step")
            .isEmpty();
    }

    private static Object terminalSet() throws Exception {
        Field f = WorkflowEvidenceBridge.class.getDeclaredField("TERMINAL");
        f.setAccessible(true);
        return f.get(null);
    }
}
