package com.tss.grc.module.workflow_submission.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import com.tss.grc.module.workflow_submission.repository.WorkflowSubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Coverage for the generic wizard ledger (V125 workflow_submissions).
 *
 * <ul>
 *   <li>{@code create()} stamps the organisation, defaults status to SUBMITTED,
 *       validates the four required fields, and preserves the typed envelope</li>
 *   <li>{@code review()} maps the four supported decisions, rejects unknowns,
 *       and refuses to mutate a row from another tenant (404)</li>
 *   <li>{@code list*()} variants all funnel through the right tenant-scoped
 *       repository finder</li>
 * </ul>
 *
 * <p>These tests guard against the most likely regressions: a cross-tenant
 * leak in review(), a default-status drift, and a silent validation pass on
 * blank required fields.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowSubmissionServiceTest {

    private static final Long ORG_ID    = 7L;
    private static final Long OTHER_ORG = 99L;

    @Mock private WorkflowSubmissionRepository repo;
    @InjectMocks private WorkflowSubmissionService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(WorkflowSubmission.class))).thenAnswer(inv -> {
            WorkflowSubmission s = inv.getArgument(0);
            if (s.getId() == null) s.setId(123L);
            return s;
        });
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── create() ────────────────────────────────────────────────────────

    @Test void create_persistsEnvelopeWithCurrentTenant() {
        var body = new WorkflowSubmissionService.CreateBody(
            "governance-board-pack", "GOVERNANCE",
            "Board pack Q2 2026 (BOARD)", "ملف المجلس",
            "9 sections · approvals: CRO+CISO+CEO · watermarked", null,
            null, null,
            "{\"quarter\":\"Q2 2026\",\"audience\":\"BOARD\"}");

        var saved = service.create(body);

        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getWorkflowId()).isEqualTo("governance-board-pack");
        assertThat(saved.getWorkflowModule()).isEqualTo("GOVERNANCE");
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");        // default
        assertThat(saved.getTitleEn()).isEqualTo("Board pack Q2 2026 (BOARD)");
        assertThat(saved.getSummaryEn()).contains("9 sections");
        assertThat(saved.getPayloadJson()).contains("Q2 2026");
        assertThat(saved.getSubmittedAt()).isNotNull();
    }

    @Test void create_honoursExplicitStatusAndRegulatoryAnchor() {
        var body = new WorkflowSubmissionService.CreateBody(
            "pdpl-cross-border", "PDPL",
            "Transfer: Acme Inc. (UAE)", null,
            "SCC · STANDARD · LT_10K · SDO=SDO-2026-0042 · review every 12m", null,
            "PDPL Art. 29", "APPROVED",
            "{\"recipientName\":\"Acme Inc.\"}");

        var saved = service.create(body);

        assertThat(saved.getStatus()).isEqualTo("APPROVED");
        assertThat(saved.getRegulatoryAnchor()).isEqualTo("PDPL Art. 29");
    }

    @Test void create_blankWorkflowId_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "", "ERM", "x", null, null, null, null, null, "{}");
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workflowId");
    }

    @Test void create_blankModule_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "", "x", null, null, null, null, null, "{}");
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workflowModule");
    }

    @Test void create_blankTitle_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "ERM", " ", null, null, null, null, null, "{}");
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("titleEn");
    }

    @Test void create_blankPayload_rejects() {
        var body = new WorkflowSubmissionService.CreateBody(
            "erm-risk-treatment", "ERM", "Some title", null, null, null, null, null, "");
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payloadJson");
    }

    // ── review() — state machine ───────────────────────────────────────

    @Test void review_approveSetsApprovedStatus() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("APPROVE", "Looks good"));

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getReviewerNote()).isEqualTo("Looks good");
        assertThat(out.getReviewedAt()).isNotNull();
    }

    @Test void review_rejectSetsRejectedStatus() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("reject", null));

        assertThat(out.getStatus()).isEqualTo("REJECTED");
    }

    @Test void review_closeSetsClosedStatus() {
        var existing = sampleSaved(ORG_ID, 555L, "APPROVED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("close", null));

        assertThat(out.getStatus()).isEqualTo("CLOSED");
    }

    @Test void review_underReviewIsSupported() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        var out = service.review(555L, new WorkflowSubmissionService.ReviewBody("UNDER_REVIEW", null));

        assertThat(out.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test void review_unknownDecisionRejected() {
        var existing = sampleSaved(ORG_ID, 555L, "SUBMITTED");
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.review(555L,
                new WorkflowSubmissionService.ReviewBody("DEFER", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DEFER");
    }

    @Test void review_otherTenantsRow_throwsNotFound() {
        // Repository must not return rows from other tenants — this is the
        // canonical cross-tenant FK leak guard.
        when(repo.findByOrganisationIdAndId(ORG_ID, 555L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(555L,
                new WorkflowSubmissionService.ReviewBody("APPROVE", null)))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("555");
    }

    // ── list*() — tenant-scoped finders ────────────────────────────────

    @Test void list_usesOrgScopedFinder() {
        when(repo.findByOrganisationIdOrderBySubmittedAtDesc(ORG_ID))
            .thenReturn(java.util.List.of(sampleSaved(ORG_ID, 1L, "SUBMITTED")));

        var rows = service.list();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test void listByWorkflowId_filtersByCatalogId() {
        when(repo.findByOrganisationIdAndWorkflowIdOrderBySubmittedAtDesc(ORG_ID, "incident-rca"))
            .thenReturn(java.util.List.of(sampleSaved(ORG_ID, 11L, "APPROVED")));

        var rows = service.listByWorkflowId("incident-rca");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(11L);
    }

    @Test void listByModule_filtersByBucket() {
        when(repo.findByOrganisationIdAndWorkflowModuleOrderBySubmittedAtDesc(ORG_ID, "GOVERNANCE"))
            .thenReturn(java.util.List.of(sampleSaved(ORG_ID, 22L, "SUBMITTED")));

        var rows = service.listByModule("GOVERNANCE");

        assertThat(rows).hasSize(1);
    }

    @Test void get_otherTenantsRow_throwsNotFound() {
        when(repo.findByOrganisationIdAndId(ORG_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void noTenant_throwsIllegalState() {
        TenantContext.clear();
        var body = new WorkflowSubmissionService.CreateBody(
            "x", "ERM", "x", null, null, null, null, null, "{}");

        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tenant");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private WorkflowSubmission sampleSaved(Long org, Long id, String status) {
        var s = new WorkflowSubmission();
        s.setId(id);
        s.setOrganisationId(org);
        s.setWorkflowId("erm-risk-treatment");
        s.setWorkflowModule("ERM");
        s.setTitleEn("Sample");
        s.setStatus(status);
        s.setPayloadJson("{}");
        return s;
    }
}
