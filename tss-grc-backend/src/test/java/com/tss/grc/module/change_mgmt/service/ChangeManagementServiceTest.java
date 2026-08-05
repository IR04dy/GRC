package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.dto.CabVoteRequest;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChangeManagementService — Sprint 4 §4.1 test backlog.
 *
 * ITIL Change Enablement state machine (13 states):
 *   DRAFT → SUBMITTED → IMPACT_ASSESSED → CAB_PENDING → CAB_APPROVED → SCHEDULED →
 *   IMPLEMENTING → IMPLEMENTED → POST_REVIEW → CLOSED
 *
 *   EMERGENCY path: DRAFT → EMERGENCY_APPROVED → IMPLEMENTING → ...
 *   Rejection / cancel paths: CAB_REJECTED → DRAFT, ROLLED_BACK → POST_REVIEW
 *
 * Tests assert:
 *   1. Happy-path normal change walks DRAFT → SUBMITTED
 *   2. Emergency-approval requires changeType=EMERGENCY
 *   3. Side-effects: cabDecisionAt + cabDecisionNotes on CAB_APPROVED
 *   4. IMPLEMENTING stamps implementationStarted; IMPLEMENTED stamps implementationEnded + outcome
 *   5. ROLLED_BACK forces outcome=ROLLED_BACK
 *   6. POST_REVIEW + CLOSED stamp date fields
 *   7. Illegal transitions throw IllegalArgumentException with message
 *   8. CAB_REJECTED → DRAFT (rework path) succeeds
 *   9. Cross-tenant request returns EntityNotFoundException
 */
@ExtendWith(MockitoExtension.class)
class ChangeManagementServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long CHANGE_ID = 100L;

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeApprovalRepository approvalRepo;
    @Mock private ChangeTaskRepository taskRepo;
    @Mock private ChangeLinkRepository linkRepo;
    @Mock private ChangeCommentRepository commentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private ChangeManagementService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // F2-29: CAB-decision targets (EMERGENCY_APPROVED / CAB_APPROVED / CAB_REJECTED) now require
        // CHANGE:APPROVE. This class tests the ITIL lifecycle, not authorisation — that is
        // ChangeDecisionAuthorityTest's job — so it runs as a persona holding both, which is what the
        // five decision-target tests below always implicitly assumed.
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("cab-member", "n/a",
                List.of(new SimpleGrantedAuthority("CHANGE_WRITE"),
                        new SimpleGrantedAuthority("CHANGE_APPROVE"))));
    }

    @AfterEach void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static ChangeRequest change(String status, String type) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG_ID);
        c.setStatus(status);
        c.setChangeType(type);
        return c;
    }

    private static ChangeApproval approve() {
        ChangeApproval a = new ChangeApproval();
        a.setDecision("APPROVE");
        a.setApproverName("Bob CAB");
        return a;
    }

    // ─── Happy path ──────────────────────────────────────────────────

    @Test
    void transition_DRAFT_to_SUBMITTED_succeeds() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "SUBMITTED", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void transition_CAB_PENDING_to_CAB_APPROVED_records_decision() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CAB_PENDING", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(approve()));

        Map<String, Object> body = new HashMap<>();
        body.put("notes", "Approved by CAB on 2026-05-08");
        ChangeRequest out = service.transition(CHANGE_ID, "CAB_APPROVED", body);

        assertThat(out.getStatus()).isEqualTo("CAB_APPROVED");
        assertThat(out.getCabDecisionAt()).isNotNull();
        assertThat(out.getCabDecisionNotes()).contains("Approved by CAB");
    }

    // ─── Emergency path ──────────────────────────────────────────────

    @Test
    void transition_EMERGENCY_APPROVED_succeeds_when_changeType_is_EMERGENCY() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "EMERGENCY")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "EMERGENCY_APPROVED", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("EMERGENCY_APPROVED");
        assertThat(out.getCabDecisionAt()).isNotNull();
    }

    @Test
    void transition_EMERGENCY_APPROVED_rejected_when_changeType_is_NORMAL() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "NORMAL")));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "EMERGENCY_APPROVED", new HashMap<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Emergency-approval path is only valid for EMERGENCY");
    }

    // ─── Implementation side effects ─────────────────────────────────

    @Test
    void transition_to_IMPLEMENTING_stamps_implementationStarted() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("SCHEDULED", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "IMPLEMENTING", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("IMPLEMENTING");
        assertThat(out.getImplementationStarted()).isNotNull();
    }

    @Test
    void transition_to_IMPLEMENTED_stamps_end_and_default_outcome_SUCCESS() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTING", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "IMPLEMENTED", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("IMPLEMENTED");
        assertThat(out.getImplementationEnded()).isNotNull();
        assertThat(out.getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void transition_to_IMPLEMENTED_with_explicit_outcome_uses_it() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTING", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("outcome", "PARTIAL_SUCCESS");
        ChangeRequest out = service.transition(CHANGE_ID, "IMPLEMENTED", body);
        assertThat(out.getOutcome()).isEqualTo("PARTIAL_SUCCESS");
    }

    @Test
    void transition_to_ROLLED_BACK_forces_outcome_to_ROLLED_BACK() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTING", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("outcome", "SUCCESS");          // attempted override should be ignored
        ChangeRequest out = service.transition(CHANGE_ID, "ROLLED_BACK", body);
        assertThat(out.getOutcome()).isEqualTo("ROLLED_BACK");
    }

    // ─── Closure paths ───────────────────────────────────────────────

    @Test
    void transition_to_POST_REVIEW_stamps_postReviewDate() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTED", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "POST_REVIEW", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("POST_REVIEW");
        assertThat(out.getPostReviewDate()).isNotNull();
    }

    @Test
    void transition_POST_REVIEW_to_CLOSED_stamps_closureDate() {
        ChangeRequest reviewed = change("POST_REVIEW", "NORMAL");
        reviewed.setPirSummary("Deployed cleanly; monitored 24h, no regressions.");  // CH-11: PIR recorded
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(reviewed));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "CLOSED", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("CLOSED");
        assertThat(out.getClosureDate()).isNotNull();
    }

    // ─── CH-11: change-plan + PIR objects ────────────────────────────

    @Test
    void transition_to_CLOSED_without_pir_blocks_normal_change() {
        // Mandatory retrospective: a NORMAL change with no PIR summary cannot close.
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("POST_REVIEW", "NORMAL")));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "CLOSED", new HashMap<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("post-implementation review (PIR) summary is required");
    }

    @Test
    void transition_to_CLOSED_without_pir_allowed_for_standard_change() {
        // STANDARD (pre-approved) changes are exempt from the mandatory PIR gate.
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("POST_REVIEW", "STANDARD")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "CLOSED", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("CLOSED");
        assertThat(out.getClosureDate()).isNotNull();
    }

    @Test
    void transition_to_POST_REVIEW_captures_pir_content_and_stamps_signoff() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTED", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("pirSummary", "Change succeeded; one minor follow-up.");
        body.put("pirObjectivesMet", true);
        body.put("pirLessons", "Stage the config change earlier next time.");
        body.put("pirFollowUp", "Open CAPA to automate the pre-flight check.");
        // CE-15: still supplied, and deliberately IGNORED now — see the assertion below.
        body.put("reviewer", "Dana Reviewer");
        ChangeRequest out = service.transition(CHANGE_ID, "POST_REVIEW", body);

        assertThat(out.getStatus()).isEqualTo("POST_REVIEW");
        assertThat(out.getPostReviewDate()).isNotNull();
        assertThat(out.getPirSummary()).contains("succeeded");
        assertThat(out.getPirObjectivesMet()).isTrue();
        assertThat(out.getPirLessons()).contains("Stage the config");
        assertThat(out.getPirFollowUp()).contains("CAPA");
        // ⚠ CE-15 changed this assertion, and the change IS the point. The reviewer used to be
        // whatever string the caller put in `reviewer` — which made "the PIR approver may not be the
        // implementer" unenforceable, because one request supplied both sides of the comparison. The
        // reviewer is now the authenticated principal, exactly as F2-93/F2-103 made the CAB decision
        // author the principal. A client-supplied name is still recorded when there is NO principal at
        // all (an off-request path), but it can never override one — that override would be the bypass.
        assertThat(out.getPirReviewedBy())
            .as("the signed-in principal, not the name in the body")
            .isEqualTo("cab-member");
        assertThat(out.getPirReviewedAt()).isNotNull();
    }

    // ─── Rework path (CAB_REJECTED → DRAFT) ──────────────────────────

    @Test
    void transition_CAB_REJECTED_to_DRAFT_succeeds_for_rework() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CAB_REJECTED", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "DRAFT", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("DRAFT");
    }

    // ─── Illegal transitions ─────────────────────────────────────────

    @Test
    void transition_DRAFT_to_IMPLEMENTING_skips_review_and_throws() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "NORMAL")));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "IMPLEMENTING", new HashMap<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot transition from DRAFT to IMPLEMENTING");
    }

    @Test
    void transition_CLOSED_is_terminal() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CLOSED", "NORMAL")));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "DRAFT", new HashMap<>()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot transition from CLOSED");
    }

    @Test
    void transition_CANCELLED_is_terminal() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CANCELLED", "NORMAL")));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "DRAFT", new HashMap<>()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Tenant scope ────────────────────────────────────────────────

    @Test
    void transition_other_tenant_throws_EntityNotFound() {
        ChangeRequest other = change("DRAFT", "NORMAL");
        other.setOrganisationId(999L);
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "SUBMITTED", new HashMap<>()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void transition_unknown_id_throws_EntityNotFound() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "SUBMITTED", new HashMap<>()))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── F7 / F8 emission contract ─────────────────────────────────────────────

    @Test
    void create_emits_CHANGE_CREATED_and_writes_audit_log() {
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> {
            ChangeRequest c = inv.getArgument(0);
            c.setId(CHANGE_ID);
            return c;
        });

        ChangeRequest req = new ChangeRequest();
        req.setChangeCode("CHG-2026-001");
        req.setTitleEn("Database failover test");
        req.setChangeType("NORMAL");
        req.setRiskLevel("MEDIUM");
        service.create(req);

        verify(audit).log(eq("CREATE"), eq("ChangeRequest"), eq(CHANGE_ID), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CHANGE_CREATED");
        assertThat(emitted.payload()).containsEntry("changeCode", "CHG-2026-001");
        assertThat(emitted.payload()).containsEntry("riskLevel", "MEDIUM");
        assertThat(emitted.payload()).containsEntry("status", "DRAFT");
    }

    @Test
    void transition_to_CAB_APPROVED_emits_CHANGE_CAB_APPROVED() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CAB_PENDING", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(approve()));

        Map<String, Object> body = new HashMap<>();
        body.put("notes", "Approved unanimously");
        service.transition(CHANGE_ID, "CAB_APPROVED", body);

        verify(audit, atLeastOnce()).log(eq("TRANSITION"), eq("ChangeRequest"), any(), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CHANGE_CAB_APPROVED");
        assertThat(emitted.payload()).containsEntry("status", "CAB_APPROVED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "CAB_PENDING");
    }

    @Test
    void transition_to_EMERGENCY_APPROVED_emits_CHANGE_EMERGENCY_APPROVED() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "EMERGENCY")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(CHANGE_ID, "EMERGENCY_APPROVED", new HashMap<>());

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CHANGE_EMERGENCY_APPROVED");
        assertThat(emitted.payload()).containsEntry("changeType", "EMERGENCY");
        assertThat(emitted.payload()).containsEntry("previousStatus", "DRAFT");
    }

    @Test
    void transition_to_ROLLED_BACK_emits_CHANGE_FAILED() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTING", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(CHANGE_ID, "ROLLED_BACK", new HashMap<>());

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CHANGE_FAILED");
        assertThat(emitted.payload()).containsEntry("status", "ROLLED_BACK");
        assertThat(emitted.payload()).containsEntry("outcome", "ROLLED_BACK");
    }

    // ── CM-C3: lifecycle-integrity — status cannot be set via create()/update() ──

    @Test
    void update_does_not_change_status_even_if_body_sets_it() {
        // A PUT carrying status=CLOSED on a DRAFT change must NOT close it —
        // status changes may only happen through the guarded transition().
        ChangeRequest existing = change("DRAFT", "NORMAL");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(existing));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest body = new ChangeRequest();
        body.setStatus("CLOSED");            // attempted lifecycle bypass
        body.setTitleEn("edited title");     // legitimate metadata edit
        ChangeRequest out = service.update(CHANGE_ID, body);

        assertThat(out.getStatus()).isEqualTo("DRAFT");        // bypass blocked
        assertThat(out.getTitleEn()).isEqualTo("edited title"); // metadata still applied
    }

    @Test
    void create_ignores_client_supplied_status_and_defaults_DRAFT() {
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> {
            ChangeRequest c = inv.getArgument(0);
            c.setId(CHANGE_ID);
            return c;
        });

        ChangeRequest req = new ChangeRequest();
        req.setTitleEn("New change");
        req.setChangeType("NORMAL");
        req.setStatus("PUBLISHED");          // attempted; must be ignored on create
        ChangeRequest out = service.create(req);

        assertThat(out.getStatus()).isEqualTo("DRAFT");
    }

    // ── CH-3: audit coverage on the previously-silent mutating ops ──────────────

    @Test
    void update_writes_audit_log() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest body = new ChangeRequest();
        body.setTitleEn("edited");
        service.update(CHANGE_ID, body);

        verify(audit).log(eq("UPDATE"), eq("ChangeRequest"), eq(CHANGE_ID), any(String.class));
    }

    @Test
    void delete_writes_audit_log() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(CHANGE_ID);

        verify(audit).log(eq("DELETE"), eq("ChangeRequest"), eq(CHANGE_ID), any(String.class));
    }

    @Test
    void recordApproval_writes_audit_log() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CAB_PENDING", "NORMAL")));
        when(approvalRepo.save(any(ChangeApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordApproval(CHANGE_ID, new CabVoteRequest("APPROVE", "CAB Member", null),
            77L, "Bob CAB");

        verify(audit).log(eq("APPROVAL"), eq("ChangeRequest"), eq(CHANGE_ID), any(String.class));
    }

    // ── CM-C2: CAB quorum + requester segregation-of-duty (service layer) ───────

    @Test
    void transition_CAB_APPROVED_blocked_without_quorum() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CAB_PENDING", "NORMAL")));
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of()); // no votes

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVE vote");
    }

    @Test
    void transition_CAB_APPROVED_blocked_with_reject_on_record() {
        ChangeApproval reject = new ChangeApproval();
        reject.setDecision("REJECT"); reject.setApproverName("Carol CAB");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CAB_PENDING", "NORMAL")));
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(reject));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("REJECT");
    }

    /**
     * DF-4 changed how this is enforced. The SoD gate now compares the authenticated
     * USER ID, not a name from the body — which is the whole point: this test used to
     * pass while the defect existed, because it politely supplied the requester's real
     * name. See {@link CabVoteProvenanceTest} for the forged-actor cases.
     */
    @Test
    void recordApproval_self_vote_blocked_by_SoD() {
        ChangeRequest c = change("CAB_PENDING", "NORMAL");
        c.setRequestedById(42L);
        c.setRequestedByName("Alice Requester");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID,
                new CabVoteRequest("APPROVE", "CAB Member", null), 42L, "Alice Requester"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duty");
    }

    /** Legacy changes have no requester/owner id, so the name comparison remains as a
     *  documented fallback — never as the primary test. */
    @Test
    void recordApproval_self_vote_blocked_by_name_only_for_legacy_changes_without_ids() {
        ChangeRequest c = change("CAB_PENDING", "NORMAL");
        c.setRequestedById(null);
        c.setChangeOwnerId(null);
        c.setRequestedByName("Alice Requester");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID,
                new CabVoteRequest("APPROVE", "CAB Member", null), 99L, "Alice Requester"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duty");
    }

    // ── CH-2: invalid enums rejected with 400 (IllegalArgumentException) ────────

    @Test
    void create_rejects_invalid_changeType() {
        ChangeRequest req = new ChangeRequest();
        req.setTitleEn("x"); req.setChangeType("MADE_UP");
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("changeType");
    }

    @Test
    void create_rejects_invalid_riskLevel() {
        ChangeRequest req = new ChangeRequest();
        req.setTitleEn("x"); req.setChangeType("NORMAL"); req.setRiskLevel("NUCLEAR");
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("riskLevel");
    }

    @Test
    void transition_to_IMPLEMENTED_rejects_invalid_outcome() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("IMPLEMENTING", "NORMAL")));
        Map<String, Object> body = new HashMap<>();
        body.put("outcome", "BOGUS");
        assertThatThrownBy(() -> service.transition(CHANGE_ID, "IMPLEMENTED", body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outcome");
    }
}
