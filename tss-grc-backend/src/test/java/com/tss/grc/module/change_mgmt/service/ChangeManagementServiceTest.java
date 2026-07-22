package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
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

import java.util.HashMap;
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

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static ChangeRequest change(String status, String type) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG_ID);
        c.setStatus(status);
        c.setChangeType(type);
        return c;
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
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("POST_REVIEW", "NORMAL")));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest out = service.transition(CHANGE_ID, "CLOSED", new HashMap<>());
        assertThat(out.getStatus()).isEqualTo("CLOSED");
        assertThat(out.getClosureDate()).isNotNull();
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
}
