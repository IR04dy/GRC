package com.tss.grc.module.approvals.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.approvals.dto.ApprovalRequestDto;
import com.tss.grc.module.approvals.entity.ApprovalRequest;
import com.tss.grc.module.approvals.repository.ApprovalRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Approval-request state machine contract:
 *   PENDING   → APPROVED, REJECTED, DELEGATED, EXPIRED
 *   DELEGATED → PENDING        (with new assignee)
 *   APPROVED, REJECTED, EXPIRED → terminal
 */
@ExtendWith(MockitoExtension.class)
class ApprovalRequestServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "APR-2026-001";

    @Mock private ApprovalRequestRepository repo;
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
}
