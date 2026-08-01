package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.evidence.dto.EvidenceRequestCreateDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestResponse;
import com.tss.grc.module.evidence.service.EvidenceRequestService;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.repository.DepartmentRepository;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

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
    @Mock private CommitteeRepository                committeeRepo;
    @Mock private CommitteeMemberTermRepository      committeeMembers;
    @Mock private WorkflowRecipientListService       recipientLists;
    @Mock private NotificationService                notifications;
    @Mock private AuditLogService                    audit;
    @Mock private ApplicationEventPublisher          publisher;
    @Mock private EvidenceRequestService             evidenceRequests;
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
            3, escRole, null, true, true, null, reviewerId, null, recipients, null, null, null, null, null);
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
        assertThatThrownBy(() -> svc.respond(10L, "done", null))
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
        assertThatThrownBy(() -> svc.respond(11L, "me first", null))
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

        RequestResponse resp = svc.respond(12L, "step 1 done", null);

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

        RequestResponse resp = svc.respond(13L, "second done", null);

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

        RequestResponse resp = svc.respond(14L, "any-one done", null);

        assertThat(a.getStatus()).isEqualTo("RESPONDED");
        assertThat(b.getStatus()).isEqualTo("SKIPPED");        // ANY rule skips the rest
        assertThat(resp.status()).isEqualTo("CLOSED");          // no review gate → auto-close
        assertThat(resp.closureCondition()).isEqualTo("AUTO_ON_COMPLETION");
    }

    // ── WF-6 conditional routing (§7 #5) ────────────────────────────────────

    private CreateRequest createWithRules(String routing, List<RecipientDto> recipients,
                                          List<WorkflowRequestDtos.BranchRuleDto> rules) {
        return new CreateRequest("RISK", "Risk", 5L, "REVIEW",
            "Conditional review", null, null, routing, "ALL", "REVIEW",
            LocalDate.now().plusDays(7), 48, "MEDIUM", null,
            3, null, null, true, false, null, null, null, recipients, rules, null, null, null, null);
    }

    private static WorkflowRequestDtos.BranchRuleDto rule(Integer step, String outcome, String action, Integer target) {
        return new WorkflowRequestDtos.BranchRuleDto(step, outcome, action, target);
    }

    @Test
    void create_branchRule_backwardGoto_rejected_noCycles() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(createWithRules("SEQUENTIAL",
                List.of(user(2L, 1), user(3L, 2)),
                List.of(rule(2, "REWORK", "GOTO_STEP", 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("circular");
    }

    @Test
    void create_branchRule_gotoInParallel_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(createWithRules("PARALLEL",
                List.of(user(2L, 1), user(3L, 1)),
                List.of(rule(1, "FAST", "GOTO_STEP", 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SEQUENTIAL");
    }

    @Test
    void create_branchRule_unknownAction_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(createWithRules("SEQUENTIAL",
                List.of(user(2L, 1)),
                List.of(rule(1, "X", "TELEPORT", null))))
            ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("branch action");
    }

    @Test
    void respond_branchRule_rejectedOutcome_returnsToInitiator() {
        authAs(2L, "assignee");
        WorkflowRequest r = sentRequest(40L, "SEQUENTIAL", "ALL", null);
        r.setBranchRules("[{\"stepNo\":1,\"onOutcome\":\"REJECTED\",\"action\":\"RETURN\",\"targetStep\":null}]");
        WorkflowRequestRecipient r1 = rec(40L, 2L, 1, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(40L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 40L))
            .thenReturn(List.of(r1, rec(40L, 3L, 2, "PENDING")));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(40L, "not acceptable", "rejected");

        assertThat(r1.getResponseOutcome()).isEqualTo("REJECTED");
        assertThat(resp.status()).isEqualTo("RETURNED");                 // §7 #5: rejected → submitter
        verify(notifications).notifyUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void respond_branchRule_gotoStep_skipsIntermediateStep() {
        authAs(2L, "assignee");
        WorkflowRequest r = sentRequest(41L, "SEQUENTIAL", "ALL", null);
        r.setBranchRules("[{\"stepNo\":1,\"onOutcome\":\"HIGH_RISK\",\"action\":\"GOTO_STEP\",\"targetStep\":3}]");
        WorkflowRequestRecipient s1 = rec(41L, 2L, 1, "NOTIFIED");
        WorkflowRequestRecipient s2 = rec(41L, 3L, 2, "PENDING");
        WorkflowRequestRecipient s3 = rec(41L, 4L, 3, "PENDING");
        when(requestRepo.findByIdAndOrganisationId(41L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 41L))
            .thenReturn(List.of(s1, s2, s3));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(41L, "risk too high", "HIGH_RISK");

        assertThat(s2.getStatus()).isEqualTo("SKIPPED");                 // jumped-over step parked
        assertThat(r.getCurrentStep()).isEqualTo(3);                     // executive-approval step
        assertThat(resp.status()).isEqualTo("IN_PROGRESS");
        // step-3 recipient got the baton notification
        verify(notifications).notifyUser(any(), org.mockito.ArgumentMatchers.eq(4L),
            any(), any(), any(), any(), any(), any());
    }

    @Test
    void respond_branchRule_noMatch_fallsBackToDefaultRouting() {
        authAs(2L, "assignee");
        WorkflowRequest r = sentRequest(42L, "SEQUENTIAL", "ALL", null);
        r.setBranchRules("[{\"stepNo\":1,\"onOutcome\":\"REJECTED\",\"action\":\"RETURN\",\"targetStep\":null}]");
        WorkflowRequestRecipient s1 = rec(42L, 2L, 1, "NOTIFIED");
        WorkflowRequestRecipient s2 = rec(42L, 3L, 2, "PENDING");
        when(requestRepo.findByIdAndOrganisationId(42L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 42L))
            .thenReturn(List.of(s1, s2));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(42L, "all good", "APPROVED_OK");

        assertThat(r.getCurrentStep()).isEqualTo(2);                     // default baton pass
        assertThat(resp.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void respond_branchRule_escalate_firesEscalationPath() {
        authAs(2L, "assignee");
        WorkflowRequest r = sentRequest(43L, "PARALLEL", "ALL", null);
        r.setEscalationRole("CISO");
        r.setBranchRules("[{\"stepNo\":1,\"onOutcome\":\"BREACH\",\"action\":\"ESCALATE\",\"targetStep\":null}]");
        WorkflowRequestRecipient s1 = rec(43L, 2L, 1, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(43L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 43L))
            .thenReturn(List.of(s1));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(43L, "personal data exposed", "breach");

        assertThat(resp.status()).isEqualTo("ESCALATED");
        assertThat(resp.escalatedAt()).isNotNull();
        verify(notifications).broadcastToRole(any(), org.mockito.ArgumentMatchers.eq("CISO"),
            any(), any(), any(), any(), any(), any());
    }

    // ── WF-8 / WF-9 recipient lists, quorum, approval gate ──────────────────

    private CreateRequest createParallel(String rule, Integer threshold, String requestType,
                                         Long approverId, List<RecipientDto> recipients) {
        return new CreateRequest("RISK", "Risk", 5L, requestType,
            "Panel request", null, null, "PARALLEL", rule, "REVIEW",
            LocalDate.now().plusDays(7), 48, "MEDIUM", null,
            3, null, null, true, false, null, null, approverId, recipients, null, null, null, threshold, null);
    }

    @Test
    void create_approvalWithoutApprover_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(createParallel("ALL", null, "APPROVAL", null,
                List.of(user(2L, 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVAL requests require an approver");
    }

    @Test
    void create_quorumWithoutThreshold_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(createParallel("QUORUM", null, "REVIEW", null,
                List.of(user(2L, 1), user(3L, 1), user(4L, 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("QUORUM completion requires completionThreshold");
    }

    @Test
    void create_quorumThresholdExceedsRecipients_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(createParallel("QUORUM", 5, "REVIEW", null,
                List.of(user(2L, 1), user(3L, 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds the number of assignee recipients");
    }

    @Test
    void respond_majority_completesAtMajority_skipsRest() {
        // 3 assignee gates, MAJORITY = 2; after the 2nd response the request completes.
        authAs(3L, "second");
        WorkflowRequest r = sentRequest(60L, "PARALLEL", "MAJORITY", null);
        WorkflowRequestRecipient a = rec(60L, 2L, 1, "RESPONDED");   // first already answered
        WorkflowRequestRecipient b = rec(60L, 3L, 1, "NOTIFIED");
        WorkflowRequestRecipient c = rec(60L, 4L, 1, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(60L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 60L))
            .thenReturn(List.of(a, b, c));
        lenient().when(userRepo.findById(3L)).thenReturn(Optional.of(orgUser(3L)));

        RequestResponse resp = svc.respond(60L, "second done", null);

        assertThat(b.getStatus()).isEqualTo("RESPONDED");
        assertThat(c.getStatus()).isEqualTo("SKIPPED");             // majority met → 3rd no longer needed
        assertThat(resp.status()).isEqualTo("CLOSED");              // no gate → auto-close
    }

    @Test
    void respond_quorum_notCompleteUntilThresholdMet() {
        // 3 gates, QUORUM threshold 3 — one response is not enough.
        authAs(2L, "first");
        WorkflowRequest r = sentRequest(61L, "PARALLEL", "QUORUM", null);
        r.setCompletionThreshold(3);
        WorkflowRequestRecipient a = rec(61L, 2L, 1, "NOTIFIED");
        WorkflowRequestRecipient b = rec(61L, 3L, 1, "NOTIFIED");
        WorkflowRequestRecipient c = rec(61L, 4L, 1, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(61L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 61L))
            .thenReturn(List.of(a, b, c));
        lenient().when(userRepo.findById(2L)).thenReturn(Optional.of(orgUser(2L)));

        RequestResponse resp = svc.respond(61L, "one of three", null);

        assertThat(a.getStatus()).isEqualTo("RESPONDED");
        assertThat(resp.status()).isEqualTo("IN_PROGRESS");         // 1 < 3 → still open
        assertThat(b.getStatus()).isEqualTo("NOTIFIED");            // others NOT skipped
    }

    @Test
    void update_afterSent_conflict() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(62L, "PARALLEL", "ALL", null);   // status SENT
        when(requestRepo.findByIdAndOrganisationId(62L, ORG)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> svc.update(62L, create("PARALLEL", "ALL", "MEDIUM",
                LocalDate.now().plusDays(5), null, List.of(user(2L, 1)), null)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    // ── WF-10 multi-tier escalation ─────────────────────────────────────────

    private CreateRequest createWithTiers(String priority, String escRole,
                                          List<WorkflowRequestDtos.EscalationTierDto> tiers,
                                          List<RecipientDto> recipients) {
        return new CreateRequest("RISK", "Risk", 5L, "REVIEW",
            "Tiered request", null, null, "PARALLEL", "ALL", "REVIEW",
            LocalDate.now().plusDays(7), 48, priority, null,
            3, escRole, null, true, false, null, null, null, recipients, null, null, null, null, tiers);
    }

    private static WorkflowRequestDtos.EscalationTierDto tier(Integer level, String role, Long userId, Integer afterDays) {
        return new WorkflowRequestDtos.EscalationTierDto(level, role, userId, afterDays);
    }

    @Test
    void create_highPriority_withOnlyTierLadder_passes() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        // HIGH priority, NO escalationRole/User/ESCALATION recipient — the tier ladder satisfies §5.
        RequestResponse resp = svc.create(createWithTiers("HIGH", null,
            List.of(tier(null, "LINE_MANAGER", null, 0), tier(null, "CISO", null, 2)),
            List.of(user(2L, 1))));
        assertThat(resp.status()).isEqualTo("DRAFT");
        assertThat(resp.escalationTiers()).hasSize(2);
        assertThat(resp.escalationTiers().get(0).level()).isEqualTo(1);   // levels assigned
    }

    @Test
    void create_tierWithoutTarget_rejected() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        // validateTiers throws before recipient validation → no userRepo stub needed.
        assertThatThrownBy(() -> svc.create(createWithTiers("MEDIUM", null,
                List.of(tier(1, null, null, 0)), List.of(user(2L, 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("role or a userId");
    }

    @Test
    void escalate_steps_up_one_tier_each_call() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(70L, "PARALLEL", "ALL", null);
        r.setEscalationTiers("[{\"level\":1,\"role\":\"LINE_MANAGER\",\"afterDays\":0},"
            + "{\"level\":2,\"role\":\"CISO\",\"afterDays\":2}]");
        r.setEscalationLevel(0);
        when(requestRepo.findByIdAndOrganisationId(70L, ORG)).thenReturn(Optional.of(r));

        RequestResponse resp1 = svc.escalate(70L, "first");
        assertThat(resp1.status()).isEqualTo("ESCALATED");
        assertThat(r.getEscalationLevel()).isEqualTo(1);
        verify(notifications).broadcastToRole(any(), org.mockito.ArgumentMatchers.eq("LINE_MANAGER"),
            any(), any(), any(), any(), any(), any());

        // second escalate steps to tier 2 (ESCALATED -> ESCALATED is allowed by the machine)
        RequestResponse resp2 = svc.escalate(70L, "second");
        assertThat(r.getEscalationLevel()).isEqualTo(2);
        verify(notifications).broadcastToRole(any(), org.mockito.ArgumentMatchers.eq("CISO"),
            any(), any(), any(), any(), any(), any());
    }

    // ── WF-7 external tokenized public access (§7 #18) ──────────────────────

    private WorkflowRequestRecipient externalRec(Long requestId, String email, String status) {
        WorkflowRequestRecipient x = new WorkflowRequestRecipient();
        x.setId(ids.incrementAndGet());
        x.setOrganisationId(ORG);
        x.setRequestId(requestId);
        x.setRecipientType("EXTERNAL");
        x.setRecipientKey(email);
        x.setRecipientLabel(email);
        x.setActorRole("ASSIGNEE");
        x.setStepNo(1);
        x.setStatus(status);
        return x;
    }

    @Test
    void send_externalRecipient_mintsAccessTokenAndExpiry() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(50L, "PARALLEL", "ALL", null);
        r.setStatus("DRAFT");
        r.setRequestType("REVIEW");
        r.setDueDate(LocalDate.now().plusDays(5));
        WorkflowRequestRecipient ext = externalRec(50L, "auditor@firm.com", "PENDING");
        when(requestRepo.findByIdAndOrganisationId(50L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 50L))
            .thenReturn(List.of(ext));

        svc.send(50L);

        assertThat(ext.getAccessToken()).isNotNull().hasSize(32);   // UUID, dashes stripped
        assertThat(ext.getTokenExpiresAt()).isNotNull();
        assertThat(ext.getStatus()).isEqualTo("NOTIFIED");
    }

    @Test
    void publicView_wrongToken_404() {
        when(recipientRepo.findByAccessToken("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.publicView("nope"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void publicRespondByToken_expiredToken_410() {
        WorkflowRequestRecipient ext = externalRec(51L, "ext@firm.com", "NOTIFIED");
        ext.setAccessToken("tok-expired");
        ext.setTokenExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        when(recipientRepo.findByAccessToken("tok-expired")).thenReturn(Optional.of(ext));
        assertThatThrownBy(() -> svc.publicRespondByToken("tok-expired", "late", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("410");
    }

    @Test
    void publicRespondByToken_alreadyResponded_409() {
        WorkflowRequestRecipient ext = externalRec(52L, "ext@firm.com", "RESPONDED");
        ext.setAccessToken("tok-used");
        when(recipientRepo.findByAccessToken("tok-used")).thenReturn(Optional.of(ext));
        assertThatThrownBy(() -> svc.publicRespondByToken("tok-used", "again", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void publicRespondByToken_valid_recordsResponse_noPrincipal() {
        WorkflowRequest r = sentRequest(53L, "PARALLEL", "ANY", null);   // no reviewer → auto-close
        WorkflowRequestRecipient ext = externalRec(53L, "ext@firm.com", "NOTIFIED");
        ext.setAccessToken("tok-live");
        ext.setTokenExpiresAt(LocalDate.now().plusDays(5).atStartOfDay());
        when(recipientRepo.findByAccessToken("tok-live")).thenReturn(Optional.of(ext));
        when(requestRepo.findById(53L)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 53L))
            .thenReturn(List.of(ext));

        WorkflowRequestDtos.PublicRequestView view = svc.publicRespondByToken("tok-live", "acknowledged", null);

        assertThat(ext.getStatus()).isEqualTo("RESPONDED");
        assertThat(ext.getRespondedBy()).isNull();                  // external — no platform principal
        assertThat(view.status()).isEqualTo("CLOSED");              // ANY + no gate → auto-close
    }

    // ── WF-5 committee recipient fan-out ────────────────────────────────────

    private static CommitteeMemberTerm term(Long memberUserId) {
        CommitteeMemberTerm t = new CommitteeMemberTerm();
        t.setMemberUserId(memberUserId);
        t.setMemberName(memberUserId == null ? "External Advisor" : "Member " + memberUserId);
        return t;
    }

    private WorkflowRequestRecipient committeeRec(Long requestId, Long committeeId, String status) {
        WorkflowRequestRecipient x = new WorkflowRequestRecipient();
        x.setId(ids.incrementAndGet());
        x.setOrganisationId(ORG);
        x.setRequestId(requestId);
        x.setRecipientType("COMMITTEE");
        x.setRecipientId(committeeId);
        x.setActorRole("ASSIGNEE");
        x.setStepNo(1);
        x.setStatus(status);
        return x;
    }

    @Test
    void create_committeeRecipient_mustExistInOrganisation() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        when(committeeRepo.findByIdAndOrganisationId(55L, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.create(create("PARALLEL", "ALL", "MEDIUM",
                LocalDate.now().plusDays(7), null,
                List.of(new RecipientDto("COMMITTEE", 55L, null, null, "ASSIGNEE", 1)), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("COMMITTEE recipient 55 not found");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void send_committeeRecipient_fansOutToActiveMembersWithUsers() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(30L, "PARALLEL", "ALL", null);
        r.setStatus("DRAFT");
        r.setRequestType("APPROVAL");
        when(requestRepo.findByIdAndOrganisationId(30L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 30L))
            .thenReturn(List.of(committeeRec(30L, 5L, "PENDING")));
        // Two linked members + one external advisor without a platform user.
        when(committeeMembers.findActiveByCommittee(ORG, 5L))
            .thenReturn(List.of(term(7L), term(8L), term(null)));

        svc.send(30L);

        org.mockito.ArgumentCaptor<Long> uid = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(notifications, org.mockito.Mockito.times(2)).notifyUser(
            org.mockito.ArgumentMatchers.eq(ORG), uid.capture(), any(), any(), any(), any(), any(), any());
        assertThat(uid.getAllValues()).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void respond_activeCommitteeMember_isActionable_nonMemberIsNot() {
        // The request is addressed to committee 5; user 7 sits on it, user 9 does not.
        WorkflowRequest r = sentRequest(31L, "PARALLEL", "ALL", null);
        WorkflowRequestRecipient row = committeeRec(31L, 5L, "NOTIFIED");
        when(requestRepo.findByIdAndOrganisationId(31L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 31L))
            .thenReturn(List.of(row));
        lenient().when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));

        authAs(9L, "outsider");
        when(committeeMembers.findActiveCommitteeIdsForUser(ORG, 9L)).thenReturn(List.of());
        assertThatThrownBy(() -> svc.respond(31L, "not my committee", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an actionable recipient");

        authAs(7L, "boardmember");
        when(committeeMembers.findActiveCommitteeIdsForUser(ORG, 7L)).thenReturn(List.of(5L));
        RequestResponse resp = svc.respond(31L, "committee acknowledged", null);
        assertThat(row.getStatus()).isEqualTo("RESPONDED");
        assertThat(resp.status()).isEqualTo("CLOSED");   // no review gate → auto-close
    }

    // ── WF-4 evidence bridge (send side) ────────────────────────────────────

    @Test
    void send_evidenceRequest_opensBridgedEvidenceRequest_andRecordsId() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(20L, "PARALLEL", "ALL", null);
        r.setStatus("DRAFT");
        r.setEvidenceRequired(true);            // requestType is already EVIDENCE_REQUEST
        WorkflowRequestRecipient assignee = rec(20L, 2L, 1, "PENDING");
        when(requestRepo.findByIdAndOrganisationId(20L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 20L))
            .thenReturn(List.of(assignee));
        EvidenceRequestResponse bridged = new EvidenceRequestResponse(99L, java.util.UUID.randomUUID(),
            "REQ-00099", null, null, null, null, "WORKFLOW_REQUEST", 20L, "t", null, null,
            null, null, null, null, null, null, null, "MEDIUM", "OPEN", null, null, null);
        when(evidenceRequests.create(any(), any())).thenReturn(bridged);

        RequestResponse resp = svc.send(20L);

        assertThat(resp.status()).isEqualTo("SENT");
        assertThat(resp.evidenceRequestId()).isEqualTo(99L);
        org.mockito.ArgumentCaptor<EvidenceRequestCreateDto> cap =
            org.mockito.ArgumentCaptor.forClass(EvidenceRequestCreateDto.class);
        verify(evidenceRequests).create(cap.capture(), any());
        assertThat(cap.getValue().entityType()).isEqualTo("WORKFLOW_REQUEST");
        assertThat(cap.getValue().entityId()).isEqualTo(20L);
        assertThat(cap.getValue().assignedToId()).isEqualTo(2L);   // first USER assignee carried
    }

    @Test
    void send_nonEvidenceType_doesNotOpenBridge() {
        authAs(1L, "admin", "PLATFORM_ADMIN");
        WorkflowRequest r = sentRequest(21L, "PARALLEL", "ALL", null);
        r.setStatus("DRAFT");
        r.setRequestType("APPROVAL");
        r.setEvidenceRequired(true);
        when(requestRepo.findByIdAndOrganisationId(21L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 21L))
            .thenReturn(List.of(rec(21L, 2L, 1, "PENDING")));

        RequestResponse resp = svc.send(21L);

        assertThat(resp.status()).isEqualTo("SENT");
        assertThat(resp.evidenceRequestId()).isNull();
        verify(evidenceRequests, never()).create(any(), any());
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

    // ── Adoption — list filtered by source module (live per-module panel) ─────

    @Test
    void list_filtersBySourceModule() {
        WorkflowRequest r = sentRequest(500L, "PARALLEL", "ALL", null);
        r.setSourceModule("CYBER");
        var pageable = PageRequest.of(0, 50);
        when(requestRepo.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(ORG, "CYBER", pageable))
            .thenReturn(new PageImpl<>(List.of(r)));

        // lower-case module in → service upper-cases and routes to the module-scoped finder
        var result = svc.list(null, "cyber", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(500L);
        assertThat(result.getContent().get(0).sourceModule()).isEqualTo("CYBER");
        verify(requestRepo).findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(ORG, "CYBER", pageable);
        verify(requestRepo, never()).findByOrganisationIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void list_filtersBySourceRecord_takesPrecedence() {
        WorkflowRequest r = sentRequest(501L, "PARALLEL", "ALL", null);
        r.setSourceModule("SOCOPS");
        r.setSourceRecordType("SecurityEvent");
        r.setSourceRecordId(4321L);
        var pageable = PageRequest.of(0, 50);
        when(requestRepo.findByOrganisationIdAndSourceRecordTypeAndSourceRecordIdOrderByCreatedAtDesc(
                ORG, "SecurityEvent", 4321L, pageable))
            .thenReturn(new PageImpl<>(List.of(r)));

        // record type+id supplied → record finder wins over the module finder
        var result = svc.list(null, "SOCOPS", "SecurityEvent", 4321L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(501L);
        verify(requestRepo).findByOrganisationIdAndSourceRecordTypeAndSourceRecordIdOrderByCreatedAtDesc(
            ORG, "SecurityEvent", 4321L, pageable);
        verify(requestRepo, never()).findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(any(), any(), any());
    }

    // ── F2-86 — four eyes on the engine 26 module screens raise requests through ─────────────
    //
    // Both halves were found by the clean-tenant probe (Appendix/scripts/clean_tenant_probe.py),
    // not by reading the code: on a tenant with no history, an initiator named itself approver,
    // submitted, approved — 200 APPROVED — and on the recipient-gated shape the rule ran backwards,
    // refusing the designated approver (400) and admitting the raiser (200).

    private CreateRequest approvalWith(Long approverId, Long reviewerId, List<RecipientDto> recipients) {
        return new CreateRequest("RISK", "Risk", 5L, "APPROVAL",
            "Approve me", null, null, "PARALLEL", "ALL", "REVIEW",
            LocalDate.now().plusDays(7), 48, "MEDIUM", null,
            3, null, null, true, false, null, reviewerId, approverId, recipients, null, null, null, null, null);
    }

    private static RecipientDto approver(Long id) {
        return new RecipientDto("USER", id, null, null, "APPROVER", 1);
    }

    /** A request already at the approval step, gated only by an APPROVER recipient row. */
    private WorkflowRequest submittedWithApproverRecipient(Long id, Long initiator, Long approverUser) {
        WorkflowRequest r = sentRequest(id, "PARALLEL", "ALL", null);
        r.setRequestType("APPROVAL");
        r.setStatus("SUBMITTED");
        r.setInitiatedBy(initiator);
        WorkflowRequestRecipient x = rec(id, approverUser, 1, "PENDING");
        x.setActorRole("APPROVER");
        when(requestRepo.findByIdAndOrganisationId(id, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, id))
            .thenReturn(List.of(x));
        return r;
    }

    @Test
    void create_initiatorAsNamedApprover_rejected() {
        authAs(1L, "raiser", "PLATFORM_ADMIN");     // admin bypasses ACTOR gates, not validation
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(approvalWith(1L, null, List.of(user(2L, 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("four-eyes");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void create_initiatorAsNamedReviewer_rejected() {
        authAs(1L, "raiser", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(approvalWith(2L, 1L, List.of(user(3L, 1)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reviewer of their own request");
    }

    @Test
    void create_initiatorAsApproverRecipient_rejected() {
        // The recipient gate is exactly as load-bearing as the named column — create() accepts it
        // as *the* approval gate — so it has to carry the same rule.
        authAs(1L, "raiser", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThatThrownBy(() -> svc.create(approvalWith(null, null, List.of(approver(1L)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVER or REVIEWER recipient");
    }

    @Test
    void create_someoneElseAsApprover_stillAllowed() {
        // The anti-vacuity half: the rule must refuse self-gating without refusing four eyes.
        authAs(1L, "raiser", "PLATFORM_ADMIN");
        when(userRepo.findById(anyLong())).thenAnswer(inv -> Optional.of(orgUser(inv.getArgument(0))));
        assertThat(svc.create(approvalWith(2L, null, List.of(user(3L, 1)))).status()).isEqualTo("DRAFT");
        assertThat(svc.create(approvalWith(null, null, List.of(approver(2L)))).status()).isEqualTo("DRAFT");
    }

    @Test
    void approve_designatedApproverRecipient_isHonoured() {
        // Before F2-86 this was the 400: approve() resolved only approverId, so the person the
        // request designates as its approver could not approve it.
        submittedWithApproverRecipient(20L, 1L, 2L);
        authAs(2L, "approver");                       // NOT admin — the gate must admit them on merit
        assertThat(svc.approve(20L, "ok").status()).isEqualTo("APPROVED");
    }

    @Test
    void approve_initiatorRefused_whenAnApproverRecipientIsDesignated() {
        // ...and this was the 200: the fallback handed the gate to the initiator.
        submittedWithApproverRecipient(21L, 1L, 2L);
        authAs(1L, "raiser");
        assertThatThrownBy(() -> svc.approve(21L, "self"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("designated actor");
    }

    @Test
    void approve_initiatorWhoIsAlsoTheNamedApprover_is409() {
        // The backstop for rows written before the create-time rule: a 409, because an SoD refusal
        // is a conflict with the request's own history rather than a malformed call.
        WorkflowRequest r = sentRequest(22L, "PARALLEL", "ALL", null);
        r.setStatus("SUBMITTED");
        r.setInitiatedBy(1L);
        r.setApproverId(1L);
        when(requestRepo.findByIdAndOrganisationId(22L, ORG)).thenReturn(Optional.of(r));
        authAs(1L, "raiser");
        assertThatThrownBy(() -> svc.approve(22L, "self"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Four-eyes");
    }

    @Test
    void reject_usesTheSameResolvedGate() {
        // approve() and reject() are the same authority; a fix applied to one only would leave the
        // other as the way round it.
        submittedWithApproverRecipient(23L, 1L, 2L);
        authAs(2L, "approver");
        assertThat(svc.reject(23L, "no").status()).isEqualTo("REJECTED");
    }

    @Test
    void approve_withNoGateAtAll_stillFallsBackToTheInitiator() {
        // Unchanged behaviour, asserted so the fix cannot quietly break requests that designate
        // nobody (an ACK or NOTIFICATION the initiator closes out themselves).
        WorkflowRequest r = sentRequest(24L, "PARALLEL", "ALL", null);
        r.setStatus("SUBMITTED");
        r.setInitiatedBy(7L);
        when(requestRepo.findByIdAndOrganisationId(24L, ORG)).thenReturn(Optional.of(r));
        when(recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(ORG, 24L))
            .thenReturn(List.of(rec(24L, 8L, 1, "PENDING")));   // ASSIGNEE only — not a gate
        authAs(7L, "initiator");
        assertThat(svc.approve(24L, "ack").status()).isEqualTo("APPROVED");
    }
}
