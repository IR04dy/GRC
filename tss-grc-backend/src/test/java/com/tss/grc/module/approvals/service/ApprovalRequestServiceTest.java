package com.tss.grc.module.approvals.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.approvals.dto.ApprovalRequestDto;
import com.tss.grc.module.approvals.entity.ApprovalRequest;
import com.tss.grc.module.approvals.repository.ApprovalRequestRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Approval-request state machine contract:
 *   PENDING   → APPROVED, REJECTED, DELEGATED, EXPIRED
 *   DELEGATED → PENDING        (with new assignee)
 *   APPROVED, REJECTED, EXPIRED → terminal
 *
 * <p>Also locks the F7 audit + F8 event emission contract introduced
 * when the parity arc reached this service (AR-66..AR-70 / V140).
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRequestServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "APR-2026-001";

    @Mock private ApprovalRequestRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private ApprovalRequestService service;

    /** F2-226 — the raiser on a create is resolved from the principal, never the body. */
    private static final String RAISER = "raiser";

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        authAs(RAISER, 31L);
    }

    @AfterEach  void tearDown() {
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private static void authAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, ORG_ID, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

    private static ApprovalRequest req(String status) {
        ApprovalRequest r = new ApprovalRequest();
        r.setExternalId(EXT_ID);
        r.setOrganisationId(ORG_ID);
        r.setStatus(status);
        r.setRequestedBy("admin");
        r.setAssignedTo("riskmanager");
        r.setOriginalAssignedTo("riskmanager");
        r.setEntityType("risk.treatment");
        r.setEntityId("TP-2026-001");
        r.setTitleEn("Approve");
        r.setTitleAr("اعتماد");
        r.setPriority("P3");
        return r;
    }

    private static ApprovalRequestDto.TransitionRequest tx(String status) {
        ApprovalRequestDto.TransitionRequest t = new ApprovalRequestDto.TransitionRequest();
        t.setStatus(status);
        return t;
    }

    private static ApprovalRequestDto createReq() {
        ApprovalRequestDto d = new ApprovalRequestDto();
        d.setEntityType("risk.treatment");
        d.setEntityId("TP-2026-001");
        d.setTitleEn("Approve");
        d.setTitleAr("اعتماد");
        // ⚠ F2-226 — `requestedBy` is deliberately NOT set here. It is READ_ONLY on the wire
        // and the service takes it from the principal; a fixture that sets it would be
        // asserting the defect (F2-159). The one test that still sets it does so to prove the
        // server ignores it.
        d.setAssignedTo("riskmanager");
        d.setPriority("P1");
        return d;
    }

    @Test
    void transition_PENDING_to_APPROVED_records_decision() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto.TransitionRequest t = tx("APPROVED");
        // SEC-IAM-002: body decidedBy is ignored — attribution is the authenticated caller.
        t.setDecidedBy("ceo");
        t.setDecisionCommentEn("LGTM");
        ApprovalRequestDto out = service.transition(EXT_ID, t, "riskmanager", false);

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getDecidedBy()).isEqualTo("riskmanager");
        assertThat(out.getDecidedAt()).isNotNull();
        assertThat(out.getDecisionCommentEn()).isEqualTo("LGTM");
    }

    @Test
    void transition_PENDING_to_DELEGATED_swaps_assignee() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto.TransitionRequest t = tx("DELEGATED");
        t.setNewAssignedTo("auditor");
        t.setDelegationReasonEn("CRO out-of-office");
        ApprovalRequestDto out = service.transition(EXT_ID, t, "riskmanager", false);

        assertThat(out.getStatus()).isEqualTo("DELEGATED");
        assertThat(out.getAssignedTo()).isEqualTo("auditor");
        assertThat(out.getDelegatedFrom()).isEqualTo("riskmanager");
        assertThat(out.getOriginalAssignedTo()).isEqualTo("riskmanager");
    }

    @Test
    void transition_DELEGATED_back_to_PENDING_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("DELEGATED")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto out = service.transition(EXT_ID, tx("PENDING"), "riskmanager", false);
        assertThat(out.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void transition_DELEGATED_without_newAssignedTo_throws() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("DELEGATED"), "riskmanager", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DELEGATED requires newAssignedTo");
    }

    /**
     * F2-377 — the four-eyes SoD invariant (raiser != decider) is enforced at create; it must also
     * hold across delegation. Delegating a request back to its RAISER lets them re-pend it and
     * approve their own request. {@code req()} has requestedBy="admin", assignedTo="riskmanager";
     * the assignee delegates to "admin" (the raiser), which must be refused.
     */
    @Test
    void transition_DELEGATED_back_to_the_raiser_is_refused_as_self_approval() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        ApprovalRequestDto.TransitionRequest t = tx("DELEGATED");
        t.setNewAssignedTo("admin");   // the raiser

        assertThatThrownBy(() -> service.transition(EXT_ID, t, "riskmanager", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duties")
            .hasMessageContaining("admin");
        verify(repo, never()).save(any());
    }

    /** Compared case-INSENSITIVELY, like the create check — the requested_by column collates CI. */
    @Test
    void transition_DELEGATED_to_the_raiser_is_case_insensitive() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        ApprovalRequestDto.TransitionRequest t = tx("DELEGATED");
        t.setNewAssignedTo("  ADMIN ");   // the raiser, differently cased + padded

        assertThatThrownBy(() -> service.transition(EXT_ID, t, "riskmanager", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duties");
        verify(repo, never()).save(any());
    }

    @Test
    void transition_APPROVED_to_PENDING_rejected_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("APPROVED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("PENDING"), "riskmanager", false))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to")
            .containsExactly("APPROVED", "PENDING");
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "APR-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("APR-X", tx("APPROVED"), "riskmanager", false))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── SEC-IAM-002: assignee-binding + decider attribution ───────────────────

    @Test
    void transition_by_non_assignee_is_forbidden() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));

        // 'attacker' is neither the assignee (riskmanager) nor a platform admin.
        assertThatThrownBy(() -> service.transition(EXT_ID, tx("APPROVED"), "attacker", false))
            .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void transition_by_platform_admin_overrides_assignee_check() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto out = service.transition(EXT_ID, tx("APPROVED"), "admin", true);

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        // Decider attribution still binds to the (admin) principal, not the body.
        assertThat(out.getDecidedBy()).isEqualTo("admin");
    }

    // ── F2-226: the raiser and the four-eyes gate ─────────────────────────────

    /**
     * The raiser is the authenticated principal, and a body field claiming otherwise is
     * ignored.
     *
     * <p>{@code requested_at} is a FIELD INITIALISER on {@link ApprovalRequest} —
     * {@code = LocalDateTime.now()} — so the server owns WHEN with no {@code setRequestedAt}
     * anywhere. F2-223's rule: having stamped the time itself, the server is already the
     * recorder of this act, and taking WHO from the caller split one attribution across two
     * owners. F2-215's third of four filling mechanisms is why no scan could see it.
     */
    @Test
    void create_recordsTheActingPrincipalAsRaiser_notAnyBodyField() {
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest r = inv.getArgument(0);
            r.setId(43L);
            return r;
        });

        ApprovalRequestDto d = createReq();
        d.setRequestedBy("Nobody Who Raised This");   // the pre-fix forgery, verbatim

        ApprovalRequestDto out = service.create(d);

        assertThat(out.getRequestedBy())
            .as("the raiser is the principal, and the body's claim is discarded")
            .isEqualTo(RAISER);

        ArgumentCaptor<ApprovalRequest> saved = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getRequestedBy()).isEqualTo(RAISER);
        assertThat(saved.getValue().getRequestedAt())
            .as("the clock half of the attribution — the entity's own initialiser")
            .isNotNull();

        // The audit line must name the SAVED raiser, never the request's (F2-223).
        verify(audit).log(eq("CREATE"), eq("ApprovalRequest"), eq(43L),
            org.mockito.ArgumentMatchers.contains("by " + RAISER));
    }

    @Test
    void create_withNoAuthenticatedPrincipal_isRefused() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.create(createReq()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("answerable for");
        verify(repo, org.mockito.Mockito.never()).save(any(ApprovalRequest.class));
    }

    /**
     * Four eyes. {@code assignedTo} is the authorisation input {@code transition()} checks
     * (SEC-IAM-002), so whoever creates the request decides who may approve it — and before
     * this gate a WORKFLOW:WRITE+APPROVE holder could raise one to themselves and approve it.
     * Probed live pre-fix: {@code compliance} did exactly that, 200 then 200.
     */
    @Test
    void create_assignedToTheRaiser_isRefused() {
        ApprovalRequestDto d = createReq();
        d.setAssignedTo(RAISER);

        assertThatThrownBy(() -> service.create(d))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duties")
            .hasMessageContaining("may not be assigned to the person raising it");
        verify(repo, org.mockito.Mockito.never()).save(any(ApprovalRequest.class));
    }

    /**
     * The column collates case-INSENSITIVELY (F2-179), so `RAISER` and `raiser` are one
     * principal to the database. A case-sensitive Java {@code equals} would let a
     * capitalisation walk straight through the gate above.
     */
    @Test
    void create_assignedToTheRaiserInDifferentCase_isAlsoRefused() {
        ApprovalRequestDto d = createReq();
        d.setAssignedTo("  " + RAISER.toUpperCase(java.util.Locale.ROOT) + "  ");

        assertThatThrownBy(() -> service.create(d))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duties")
            .hasMessageContaining("may not be assigned to the person raising it");
    }

    @Test
    void create_assignedToSomebodyElse_stillWorks() {
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest r = inv.getArgument(0);
            r.setId(44L);
            return r;
        });

        ApprovalRequestDto out = service.create(createReq());   // assignedTo = riskmanager

        assertThat(out.getAssignedTo()).isEqualTo("riskmanager");
        assertThat(out.getOriginalAssignedTo()).isEqualTo("riskmanager");
        assertThat(out.getRequestedBy()).isEqualTo(RAISER);
        assertThat(out.getStatus()).isEqualTo("PENDING");
    }

    // ── F7 / F8 emission contract ─────────────────────────────────────────────

    @Test
    void create_emits_APPROVAL_CREATED_and_writes_audit_log() {
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest r = inv.getArgument(0);
            r.setId(42L);
            return r;
        });

        service.create(createReq());

        verify(audit).log(eq("CREATE"), eq("ApprovalRequest"), eq(42L), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("APPROVAL_CREATED");
        assertThat(emitted.organisationId()).isEqualTo(ORG_ID);
        assertThat(emitted.entityType()).isEqualTo("ApprovalRequest");
        assertThat(emitted.payload()).containsEntry("priority", "P1");
        assertThat(emitted.payload()).containsEntry("status", "PENDING");
        assertThat(emitted.payload()).containsEntry("previousStatus", "");
        assertThat(emitted.payload()).containsEntry("entityType", "risk.treatment");
    }

    @Test
    void transition_to_APPROVED_emits_APPROVAL_DECIDED_with_previousStatus() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto.TransitionRequest t = tx("APPROVED");
        t.setDecidedBy("admin");
        service.transition(EXT_ID, t, "riskmanager", false);

        verify(audit).log(eq("TRANSITION"), eq("ApprovalRequest"), any(), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("APPROVAL_DECIDED");
        assertThat(emitted.payload()).containsEntry("status", "APPROVED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "PENDING");
        assertThat(emitted.payload()).containsEntry("entityType", "risk.treatment");
    }

    @Test
    void transition_to_DELEGATED_emits_APPROVAL_DELEGATED_with_delegatedFrom() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto.TransitionRequest t = tx("DELEGATED");
        t.setNewAssignedTo("auditor");
        service.transition(EXT_ID, t, "riskmanager", false);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("APPROVAL_DELEGATED");
        assertThat(emitted.payload()).containsEntry("delegatedFrom", "riskmanager");
        assertThat(emitted.payload()).containsEntry("assignedTo", "auditor");
    }

    @Test
    void transition_to_EXPIRED_emits_APPROVAL_EXPIRED() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(EXT_ID, tx("EXPIRED"), "riskmanager", false);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("APPROVAL_EXPIRED");
        assertThat(emitted.payload()).containsEntry("status", "EXPIRED");
    }
}
