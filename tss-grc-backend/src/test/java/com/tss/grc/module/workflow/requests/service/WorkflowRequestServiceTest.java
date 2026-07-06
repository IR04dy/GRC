package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.repository.DepartmentRepository;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RequestResponse;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestRecipient;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRecipientRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WF-1 — {@link WorkflowRequestService}: §5 validation gates (no recipient / no due date /
 * no escalation path for high priority / non-contiguous sequential steps → 400), sequential
 * order enforcement, parallel ALL vs ANY completion, auto-close when no review gate, and the
 * lifecycle state machine (illegal move → 409).
 */
@ExtendWith(MockitoExtension.class)
class WorkflowRequestServiceTest {

    private static final Long ORG = 1L;

    @Mock private WorkflowRequestRepository          requestRepo;
    @Mock private WorkflowRequestRecipientRepository recipientRepo;
    @Mock private WorkflowRequestEventRepository     eventRepo;
    @Mock private UserRepository                     userRepo;
    @Mock private DepartmentRepository               departmentRepo;
    @Mock private NotificationService                notifications;
    @Mock private AuditLogService                    audit;
    @Mock private ApplicationEventPublisher          publisher;
    @InjectMocks private WorkflowRequestService      svc;

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        lenient().when(requestRepo.save(any())).thenAnswer(inv -> {
            WorkflowRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(ids.incrementAndGet());
            return r;
        });
        lenient().when(recipientRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authAs(Long userId, String username, String... roles) {
        GrcUserDetails principal = mock(GrcUserDetails.class);
        lenient().when(principal.getUserId()).thenReturn(userId);
        lenient().when(principal.getUsername()).thenReturn(username);
        var auths = Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", auths));
    }

    private User orgUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setOrganisationId(ORG);
        u.setFullNameEn("User " + id);
        return u;
    }

    private static RecipientDto user(Long id, Integer step) {
        return new RecipientDto("USER", id, null, null, "ASSIGNEE", step);
    }

    private CreateRequest create(String routing, String rule, String priority,
                                 LocalDate due, String escRole, List<RecipientDto> recipients,
                                 Long reviewerId) {
        return new CreateRequest("RISK", "Risk", 5L, "EVIDENCE_REQUEST",
            "Collect evidence", null, null, routing, rule, "UPLOAD", due, 48, priority, null,
            3, escRole, null, true, true, null, reviewerId, null, recipients);
    }

    // ── §5 validation gates ─────────────────────────────────────────────────

    @Test
    void create_noRecipients_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        assertThatThrownBy(() -> svc.create(create("PARALLEL", "ALL", "MEDIUM",
                LocalDate.now().plusDays(7), null, List.of(), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("recipient");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void create_highPriorityWithoutEscalationPath_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));
        assertThatThrownBy(() -> svc.create(create("PARALLEL", "ALL", "HIGH",
                LocalDate.now().plusDays(7), null, List.of(user(2L, 1)), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escalation");
    }

    @Test
    void create_responseRequiredWithoutDueDate_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));
        assertThatThrownBy(() -> svc.create(create("PARALLEL", "ALL", "MEDIUM",
                null, null, List.of(user(2L, 1)), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dueDate");
    }

    @Test
    void create_sequentialNonContiguousSteps_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(create("SEQUENTIAL", "ALL", "MEDIUM",
                LocalDate.now().plusDays(7), null, List.of(user(2L, 1), user(3L, 3)), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contiguous");
    }

    @Test
    void create_happyPath_savesDraft_recipients_andTimelineEvent() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));
        RequestResponse resp = svc.create(create("PARALLEL", "ALL", "MEDIUM",
            LocalDate.now().plusDays(7), null, List.of(user(2L, 1)), null));
        assertThat(resp.status()).isEqualTo("DRAFT");
        assertThat(resp.requestCode()).startsWith("WR-");
        verify(recipientRepo).save(any(WorkflowRequestRecipient.class));
        verify(eventRepo).save(any());
        verify(audit).log(any(), any(), anyLong(), any());
    }

    // ── Routing enforcement ─────────────────────────────────────────────────

    private WorkflowRequest sentRequest(Long id, String routing, String rule, Long reviewerId) {
        WorkflowRequest r = new WorkflowRequest();
        r.setId(id);
        r.setOrganisationId(ORG);
        r.setSourceModule("RISK");
        r.setRequestType("EVIDENCE_REQUEST");
        r.setTitleEn("t");
        r.setRoutingMode(routing);
        r.setCompletionRule(rule);
        r.setCurrentStep(1);
        r.setStatus("SENT");
        r.setReviewerId(reviewerId);
        r.setInitiatedBy(1L);
        return r;
    }

    private WorkflowRequestRecipient rec(Long requestId, Long userId, int step, String status) {
        WorkflowRequestRecipient x = new WorkflowRequestRecipient();
        x.setId(ids.incrementAndGet());
        x.setOrganisationId(ORG);
        x.setRequestId(requestId);
        x.setRecipientType("USER");
        x.setRecipientId(userId);
        x.setActorRole("ASSIGNEE");
        x.setStepNo(step);
        x.setStatus(status);
        return x;
    }

    @Test
    void respond_nonRecipient_rejected() {
        authAs(99L, "stranger");
        WorkflowRequest r = sentRequest(10L, "PARALLEL", "ALL", null);
        when(requestRepo.findByIdAndOrganisationId(10L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 10L))
            .thenReturn(List.of(rec(10L, 2L, 1, "NOTIFIED")));
        lenient().when(userRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.respond(10L, "done"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an actionable recipient");
    }

    @Test
    void respond_sequential_laterStepBlocked_untilCurrentStepDone() {
        // Two-step sequential chain: step 1 = user 2, step 2 = user 3. User 3 tries to jump the queue.
        authAs(3L, "step2user");
        WorkflowRequest r = sentRequest(11L, "SEQUENTIAL", "ALL", null);
        when(requestRepo.findByIdAndOrganisationId(11L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 11L))
            .thenReturn(List.of(rec(11L, 2L, 1, "NOTIFIED"), rec(11L, 3L, 2, "PENDING")));
        lenient().when(userRepo.findById(3L)).thenReturn(Optional.of(orgUser(3L)));
        assertThatThrownBy(() -> svc.respond(11L, "me first"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an actionable recipient");
    }

    @Test
    void respond_sequential_step1Response_advancesToStep2_notNotComplete() {
        authAs(2L, "step1user");
        WorkflowRequest r = sentRequest(12L, "SEQUENTIAL", "ALL", 1L);
        WorkflowRequestRecipient r1 = rec(12L, 2L, 1, "NOTIFIED");
        WorkflowRequestRecipient r2 = rec(12L, 3L, 2, "PENDING");
        when(requestRepo.findByIdAndOrganisationId(12L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 12L))
            .thenReturn(List.of(r1, r2));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(12L, "step 1 done");

        assertThat(r1.getStatus()).isEqualTo("RESPONDED");
        assertThat(r.getCurrentStep()).isEqualTo(2);          // baton passed
        assertThat(resp.status()).isEqualTo("IN_PROGRESS");   // not complete yet
        // step-2 recipient got the baton notification
        verify(notifications).notifyUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void respond_parallelAll_lastResponderCompletes_withReviewer_toSubmitted() {
        authAs(3L, "second");
        WorkflowRequest r = sentRequest(13L, "PARALLEL", "ALL", 1L);
        WorkflowRequestRecipient a = rec(13L, 2L, 1, "RESPONDED");   // first already responded
        WorkflowRequestRecipient b = rec(13L, 3L, 1, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(13L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 13L))
            .thenReturn(List.of(a, b));
        lenient().when(userRepo.findById(3L)).thenReturn(Optional.of(orgUser(3L)));

        RequestResponse resp = svc.respond(13L, "second done");

        assertThat(b.getStatus()).isEqualTo("RESPONDED");
        assertThat(resp.status()).isEqualTo("SUBMITTED");      // reviewer gate keeps it open
        assertThat(resp.closedAt()).isNull();
    }

    @Test
    void respond_parallelAny_firstResponderCompletes_skipsOthers_autoCloses() {
        authAs(2L, "first");
        WorkflowRequest r = sentRequest(14L, "PARALLEL", "ANY", null);  // no reviewer/approver
        WorkflowRequestRecipient a = rec(14L, 2L, 1, "NOTIFIED");
        WorkflowRequestRecipient b = rec(14L, 3L, 1, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(14L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 14L))
            .thenReturn(List.of(a, b));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(14L, "any-one done");

        assertThat(a.getStatus()).isEqualTo("RESPONDED");
        assertThat(b.getStatus()).isEqualTo("SKIPPED");        // ANY rule skips the rest
        assertThat(resp.status()).isEqualTo("CLOSED");          // no review gate → auto-close
        assertThat(resp.closureCondition()).isEqualTo("AUTO_ON_COMPLETION");
    }

    // ── Lifecycle machine ───────────────────────────────────────────────────

    @Test
    void approve_fromDraft_illegalTransition() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(15L, "PARALLEL", "ALL", null);
        r.setStatus("DRAFT");
        when(requestRepo.findByIdAndOrganisationId(15L, ORG)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> svc.approve(15L, null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void review_return_then_resubmit_backToUnderReview() {
        authAs(1L, "reviewer", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(16L, "PARALLEL", "ALL", 1L);
        r.setStatus("SUBMITTED");
        when(requestRepo.findByIdAndOrganisationId(16L, ORG)).thenReturn(Optional.of(r));

        RequestResponse returned = svc.review(16L, "RETURN", "fix the attachment");
        assertThat(returned.status()).isEqualTo("RETURNED");

        RequestResponse resubmitted = svc.resubmit(16L, "fixed");
        assertThat(resubmitted.status()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void reviewer_gate_nonReviewerCannotReview() {
        authAs(42L, "random");   // not the reviewer (1L), not admin
        WorkflowRequest r = sentRequest(17L, "PARALLEL", "ALL", 1L);
        r.setStatus("SUBMITTED");
        when(requestRepo.findByIdAndOrganisationId(17L, ORG)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> svc.review(17L, "APPROVE", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("designated actor");
    }
}
