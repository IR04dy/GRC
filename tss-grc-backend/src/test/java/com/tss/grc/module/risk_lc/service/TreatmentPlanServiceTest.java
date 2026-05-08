package com.tss.grc.module.risk_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk_lc.dto.TreatmentPlanDto;
import com.tss.grc.module.risk_lc.entity.TreatmentPlan;
import com.tss.grc.module.risk_lc.repository.TreatmentPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Locks the risk-lifecycle state machine contract against accidental change.
 *
 * Allowed transitions (mirrors UI client + V15 migration):
 *   DRAFT       → SUBMITTED
 *   SUBMITTED   → APPROVED | REJECTED
 *   APPROVED    → IN_PROGRESS
 *   IN_PROGRESS → COMPLETED
 *   REJECTED    → DRAFT
 */
@ExtendWith(MockitoExtension.class)
class TreatmentPlanServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "TP-2026-001";

    @Mock private TreatmentPlanRepository repo;
    @InjectMocks private TreatmentPlanService service;

    @BeforeEach
    void setUp() { TenantContext.setCurrentTenant(ORG_ID); }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static TreatmentPlan plan(String status) {
        TreatmentPlan p = new TreatmentPlan();
        p.setExternalId(EXT_ID);
        p.setOrganisationId(ORG_ID);
        p.setStatus(status);
        return p;
    }

    private static TreatmentPlanDto.TransitionRequest req(String status) {
        TreatmentPlanDto.TransitionRequest r = new TreatmentPlanDto.TransitionRequest();
        r.setStatus(status);
        return r;
    }

    // ─── Happy path ─────────────────────────────────────────────────────────

    @Test
    void transition_DRAFT_to_SUBMITTED_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(plan("DRAFT")));
        when(repo.save(any(TreatmentPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TreatmentPlanDto result = service.transition(EXT_ID, req("SUBMITTED"));

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    void transition_SUBMITTED_to_APPROVED_succeeds_and_records_approver() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(plan("SUBMITTED")));
        when(repo.save(any(TreatmentPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TreatmentPlanDto.TransitionRequest r = req("APPROVED");
        r.setApprovedBy("CRO");
        TreatmentPlanDto result = service.transition(EXT_ID, r);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getApprovedBy()).isEqualTo("CRO");
        assertThat(result.getApprovedAt()).isNotNull();
    }

    @Test
    void transition_REJECTED_back_to_DRAFT_succeeds() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(plan("REJECTED")));
        when(repo.save(any(TreatmentPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TreatmentPlanDto result = service.transition(EXT_ID, req("DRAFT"));

        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    // ─── Illegal transitions ────────────────────────────────────────────────

    @Test
    void transition_DRAFT_to_APPROVED_skips_review_and_rejects() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(plan("DRAFT")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("APPROVED")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to", "allowed")
            .containsExactly("DRAFT", "APPROVED", Set.of("SUBMITTED"));
    }

    @Test
    void transition_COMPLETED_to_anything_rejects_terminal_state() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(plan("COMPLETED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, req("DRAFT")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to", "allowed")
            .containsExactly("COMPLETED", "DRAFT", Set.of());
    }

    // ─── Not found ─────────────────────────────────────────────────────────

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "TP-DOES-NOT-EXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("TP-DOES-NOT-EXIST", req("SUBMITTED")))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("not found");
    }
}
