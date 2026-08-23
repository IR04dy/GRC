package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import com.tss.grc.module.bcm.cycle.entity.BcmGovernanceRecord;
import com.tss.grc.module.bcm.cycle.repository.BcmAssessmentCycleRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGovernanceRecordRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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
 * BcmCycleService — Sprint BCM-1 lifecycle locks.
 *
 * <p>Cycle:    DRAFT → IN_PROGRESS → UNDER_REVIEW → APPROVED → ARCHIVED.
 * <p>Gov:      DRAFT → UNDER_REVIEW → APPROVED → ACTIVE; CHANGES_REQUIRED loops.
 */
@ExtendWith(MockitoExtension.class)
class BcmCycleServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private BcmAssessmentCycleRepository cycles;
    @Mock private BcmGovernanceRecordRepository governance;
    @Mock private CommitteeRepository committees;
    @Mock private AuditLogService audit;
    @Mock private org.springframework.context.ApplicationEventPublisher publisher;
    @InjectMocks private BcmCycleService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmAssessmentCycle cycle(String status) {
        BcmAssessmentCycle c = new BcmAssessmentCycle();
        c.setId(1L); c.setOrganisationId(ORG_ID);
        c.setCycleNameEn("BCM Readiness Cycle 2025");
        c.setReportingYear((short) 2025);
        c.setStatus(status);
        return c;
    }

    private static BcmGovernanceRecord gov(String status) {
        BcmGovernanceRecord g = new BcmGovernanceRecord();
        g.setId(1L); g.setOrganisationId(ORG_ID);
        g.setPolicyTitleEn("BCM Governance Policy v1.0");
        g.setPolicyVersion("1.0");
        g.setApprovalStatus(status);
        return g;
    }

    // ── Cycle creation ─────────────────────────────────────────────

    @Test void cycle_createRequiresName() {
        BcmAssessmentCycle body = new BcmAssessmentCycle();
        body.setReportingYear((short) 2025);
        assertThatThrownBy(() -> service.createCycle(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycleNameEn");
    }

    @Test void governance_createRejectsUnknownCommittee() {
        BcmGovernanceRecord body = gov("DRAFT");
        body.setCommitteeId(999L);
        when(committees.findByIdAndOrganisationId(999L, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("committee");
    }

    @Test void cycle_createDuplicateYearRejected() {
        BcmAssessmentCycle body = cycle("DRAFT");
        when(cycles.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(Optional.of(body));
        assertThatThrownBy(() -> service.createCycle(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    // ── Cycle lifecycle ────────────────────────────────────────────

    @Test void cycle_transitionDraftToInProgressOk() {
        BcmAssessmentCycle c = cycle("DRAFT");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmAssessmentCycle out = service.transitionCycle(1L, "IN_PROGRESS", "alice");
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test void cycle_approvalStampsApprover() {
        BcmAssessmentCycle c = cycle("UNDER_REVIEW");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmAssessmentCycle out = service.transitionCycle(1L, "APPROVED", "alice");
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApprovedBy()).isEqualTo("alice");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test void cycle_underReviewCanReturnToInProgress() {
        BcmAssessmentCycle c = cycle("UNDER_REVIEW");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmAssessmentCycle out = service.transitionCycle(1L, "IN_PROGRESS", null);
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test void cycle_illegalTransitionRejected() {
        BcmAssessmentCycle c = cycle("DRAFT");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.transitionCycle(1L, "APPROVED", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void cycle_updateApprovedRefused() {
        BcmAssessmentCycle c = cycle("APPROVED");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        BcmAssessmentCycle patch = new BcmAssessmentCycle();
        patch.setCycleNameEn("Edited after approval");
        assertThatThrownBy(() -> service.updateCycle(1L, patch))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
    }

    // ── Governance lifecycle ──────────────────────────────────────

    @Test void gov_createRequiresTitle() {
        BcmGovernanceRecord body = new BcmGovernanceRecord();
        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("policyTitleEn");
    }

    @Test void gov_changesRequiredLoopsBackToDraft() {
        BcmGovernanceRecord g = gov("CHANGES_REQUIRED");
        when(governance.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        when(governance.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmGovernanceRecord out = service.transitionGovernance(1L, "DRAFT", null);
        assertThat(out.getApprovalStatus()).isEqualTo("DRAFT");
    }

    @Test void gov_approveStampsApprover() {
        BcmGovernanceRecord g = gov("UNDER_REVIEW");
        when(governance.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        when(governance.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmGovernanceRecord out = service.transitionGovernance(1L, "APPROVED", "bob");
        assertThat(out.getApprovedBy()).isEqualTo("bob");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test void gov_activeCannotJumpBackToDraft() {
        BcmGovernanceRecord g = gov("ACTIVE");
        when(governance.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.transitionGovernance(1L, "DRAFT", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void gov_invalidReviewFrequencyRejected() {
        BcmGovernanceRecord body = gov("DRAFT");
        body.setReviewFrequency("FORTNIGHTLY");
        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("review frequency");
    }

    // ── F2-22b(ii): create must not be a shortcut into an approved state ──────────────────
    //
    // Both creates read "if (status == null) DRAFT" and then validated the value against a set that
    // CONTAINS APPROVED. So POST /cycles {"status":"APPROVED"} landed an approved cycle without
    // passing a single rule in transition() — no state chain, no server-side approvedBy stamp, no
    // TRANSITION audit row. And because updateCycle() refuses to edit an APPROVED cycle, the forged
    // record was then immutable except by transition. `approvedBy`/`approvedAt` were bindable from
    // the same body, so the approval could also name anyone: DF-4's defect on a second surface.

    @Test void cycle_cannotBeCreatedAlreadyApproved() {
        BcmAssessmentCycle body = cycle(null);
        body.setId(null);
        body.setStatus("APPROVED");
        body.setApprovedBy("the_ceo");

        assertThatThrownBy(() -> service.createCycle(body))
            .isInstanceOf(IllegalArgumentException.class)
            // F2-296 — these assert the INFORMATION the refusal has to carry, not its phrasing.
            // They were `"created at DRAFT"` + `"status=APPROVED"`, which were fragments of this
            // service's own private copy of the rule; the shared primitive says the same things in
            // its own words and adds the remedy ("Send status=DRAFT, or omit it"). A test that pins
            // a wording makes the wording load-bearing, and the wording is the one part of a
            // diagnostic that should be free to improve.
            .hasMessageContaining("status")
            .hasMessageContaining("DRAFT")
            .hasMessageContaining("APPROVED")
            // the refusal has to say where to go instead, or it is just an obstacle
            .hasMessageContaining("/cycles/{id}/transition");
    }

    @Test void cycle_cannotBeCreatedMidChainEither() {
        BcmAssessmentCycle body = cycle(null);
        body.setId(null);
        body.setStatus("UNDER_REVIEW");

        assertThatThrownBy(() -> service.createCycle(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("UNDER_REVIEW");
    }

    @Test void cycle_createdAtDraftWhetherOrNotTheBodySaysSo() {
        when(cycles.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(Optional.empty());
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BcmAssessmentCycle silent = cycle(null);
        silent.setId(null);
        silent.setStatus(null);
        assertThat(service.createCycle(silent).getStatus()).isEqualTo("DRAFT");

        BcmAssessmentCycle explicit = cycle(null);
        explicit.setId(null);
        explicit.setStatus("DRAFT");
        assertThat(service.createCycle(explicit).getStatus())
            .as("a UI that posts DRAFT explicitly must keep working — that is what NdiPage does")
            .isEqualTo("DRAFT");
    }

    @Test void gov_cannotBeCreatedAlreadyActive() {
        BcmGovernanceRecord body = gov("ACTIVE");
        body.setId(null);

        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class)
            // F2-296 — the field name is the load-bearing half here: this register's create guards
            // `approvalStatus`, and until this slice the shared primitive hard-coded "status: " in
            // its message, which is the recorded reason this service hand-rolled its own copy.
            .hasMessageContaining("approvalStatus")
            .hasMessageContaining("ACTIVE")
            .hasMessageContaining("/governance/{id}/transition");
    }

    /**
     * The transition path is the one that may set them, and it stamps the actor rather than reading
     * a name from the body — asserted by {@code gov_underReviewToApprovedStampsApprover} above.
     * This pins the other half: the fields are no longer deserialisable at all, so no create or
     * update can supply them however the service is written.
     */
    @Test void approvalProvenanceIsNotDeserialisable() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        BcmGovernanceRecord bound = mapper.readValue(
            "{\"policyTitleEn\":\"BCM Policy\",\"approvedBy\":\"the_ceo\","
            + "\"approvedAt\":\"2001-01-01T00:00:00\"}", BcmGovernanceRecord.class);

        assertThat(bound.getPolicyTitleEn()).as("ordinary fields still bind").isEqualTo("BCM Policy");
        assertThat(bound.getApprovedBy()).isNull();
        assertThat(bound.getApprovedAt()).isNull();

        // ...and they must still be SERIALISED, or the approval column vanishes from every screen
        // and export that shows who approved a record (the F2-22a @JsonIgnore trap).
        BcmGovernanceRecord out = gov("APPROVED");
        out.setApprovedBy("real_approver");
        assertThat(mapper.writeValueAsString(out)).contains("\"approvedBy\":\"real_approver\"");
    }
}
