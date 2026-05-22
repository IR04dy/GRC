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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

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
        d.setRequestedBy("admin");
        d.setAssignedTo("riskmanager");
        d.setPriority("P1");
        return d;
    }

    @Test
    void transition_PENDING_to_APPROVED_records_decision() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto.TransitionRequest t = tx("APPROVED");
        t.setDecidedBy("admin");
        t.setDecisionCommentEn("LGTM");
        ApprovalRequestDto out = service.transition(EXT_ID, t);

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getDecidedBy()).isEqualTo("admin");
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
        ApprovalRequestDto out = service.transition(EXT_ID, t);

        assertThat(out.getStatus()).isEqualTo("DELEGATED");
        assertThat(out.getAssignedTo()).isEqualTo("auditor");
        assertThat(out.getDelegatedFrom()).isEqualTo("riskmanager");
        assertThat(out.getOriginalAssignedTo()).isEqualTo("riskmanager");
    }

    @Test
    void transition_DELEGATED_back_to_PENDING_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("DELEGATED")));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequestDto out = service.transition(EXT_ID, tx("PENDING"));
        assertThat(out.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void transition_DELEGATED_without_newAssignedTo_throws() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("PENDING")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("DELEGATED")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DELEGATED requires newAssignedTo");
    }

    @Test
    void transition_APPROVED_to_PENDING_rejected_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(req("APPROVED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("PENDING")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to")
            .containsExactly("APPROVED", "PENDING");
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "APR-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("APR-X", tx("APPROVED")))
            .isInstanceOf(EntityNotFoundException.class);
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
        service.transition(EXT_ID, t);

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
        service.transition(EXT_ID, t);

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

        service.transition(EXT_ID, tx("EXPIRED"));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("APPROVAL_EXPIRED");
        assertThat(emitted.payload()).containsEntry("status", "EXPIRED");
    }
}
