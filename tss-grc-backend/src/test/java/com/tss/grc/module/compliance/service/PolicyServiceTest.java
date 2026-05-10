package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Policy;
import com.tss.grc.module.compliance.repository.PolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PolicyService — Sprint 4 §4.1 test backlog.
 *
 * Locks the policy lifecycle state machine (assessment §5.3 recommendation #2):
 *
 *   DRAFT ──► LEGAL_REVIEW ──► EXEC_APPROVAL ──► PUBLISHED ──► REVIEW_DUE ──► RETIRED
 *      ▲           │                  │                              │
 *      │           ▼                  ▼                              │
 *      │       REJECTED ◄─────── REJECTED                            │
 *      │           │                                                 │
 *      └───────────┘                                                 │
 *      (re-submit)                                                   │
 *                                       PUBLISHED ───────────────────┘
 *                                                  (skip review-due retirement)
 *
 * Plus side-effects on transitions:
 *   - legalApprove stamps legalReviewedAt
 *   - executiveApprove stamps approvedAt + publishedAt + defaults effectiveFrom + nextReviewDate (+1 year)
 *   - retire stamps retiredAt
 *   - reject persists the reason string from request body
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long POL_ID    = 33L;

    @Mock private PolicyRepository repo;
    @InjectMocks private PolicyService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Policy policy(String status, Long orgId) {
        Policy p = new Policy();
        p.setId(POL_ID);
        p.setOrganisationId(orgId);
        p.setCode("POL-001");
        p.setTitleEn("Acceptable Use");
        p.setStatus(status);
        return p;
    }

    private static Policy policy(String status) { return policy(status, ORG_ID); }

    // ─── DRAFT → LEGAL_REVIEW ────────────────────────────────────────

    @Test
    void submitForLegalReview_DRAFT_to_LEGAL_REVIEW() {
        Policy p = policy("DRAFT");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy after = service.submitForLegalReview(POL_ID);
        assertThat(after.getStatus()).isEqualTo("LEGAL_REVIEW");
    }

    @Test
    void submitForLegalReview_REJECTED_to_LEGAL_REVIEW() {
        // After rejection, submitting again resumes the legal-review path
        Policy p = policy("REJECTED");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.submitForLegalReview(POL_ID).getStatus()).isEqualTo("LEGAL_REVIEW");
    }

    @Test
    void submitForLegalReview_PUBLISHED_throws() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("PUBLISHED")));

        assertThatThrownBy(() -> service.submitForLegalReview(POL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DRAFT");
    }

    // ─── legalApprove ────────────────────────────────────────────────

    @Test
    void legalApprove_LEGAL_REVIEW_to_EXEC_APPROVAL_and_stamps_legalReviewedAt() {
        Policy p = policy("LEGAL_REVIEW");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy after = service.legalApprove(POL_ID);
        assertThat(after.getStatus()).isEqualTo("EXEC_APPROVAL");
        assertThat(after.getLegalReviewedAt()).isNotNull();
    }

    @Test
    void legalApprove_from_DRAFT_throws() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("DRAFT")));

        assertThatThrownBy(() -> service.legalApprove(POL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("LEGAL_REVIEW");
    }

    // ─── executiveApprove ────────────────────────────────────────────

    @Test
    void executiveApprove_EXEC_APPROVAL_to_PUBLISHED_stamps_dates_and_defaults_review() {
        Policy p = policy("EXEC_APPROVAL");
        // both date fields blank → service should fill them
        p.setEffectiveFrom(null);
        p.setNextReviewDate(null);
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy after = service.executiveApprove(POL_ID);
        assertThat(after.getStatus()).isEqualTo("PUBLISHED");
        assertThat(after.getApprovedAt()).isNotNull();
        assertThat(after.getPublishedAt()).isNotNull();
        assertThat(after.getEffectiveFrom()).isEqualTo(LocalDate.now());
        assertThat(after.getNextReviewDate()).isEqualTo(LocalDate.now().plusYears(1));
    }

    @Test
    void executiveApprove_does_not_overwrite_existing_dates() {
        Policy p = policy("EXEC_APPROVAL");
        LocalDate explicitFrom   = LocalDate.of(2027, 1, 1);
        LocalDate explicitReview = LocalDate.of(2030, 6, 30);
        p.setEffectiveFrom(explicitFrom);
        p.setNextReviewDate(explicitReview);
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy after = service.executiveApprove(POL_ID);
        assertThat(after.getEffectiveFrom()).isEqualTo(explicitFrom);
        assertThat(after.getNextReviewDate()).isEqualTo(explicitReview);
    }

    @Test
    void executiveApprove_from_LEGAL_REVIEW_throws() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("LEGAL_REVIEW")));

        assertThatThrownBy(() -> service.executiveApprove(POL_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("EXEC_APPROVAL");
    }

    // ─── markReviewDue / retire ─────────────────────────────────────

    @Test
    void markReviewDue_PUBLISHED_to_REVIEW_DUE() {
        Policy p = policy("PUBLISHED");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.markReviewDue(POL_ID).getStatus()).isEqualTo("REVIEW_DUE");
    }

    @Test
    void retire_PUBLISHED_or_REVIEW_DUE_to_RETIRED_stamps_retiredAt() {
        // From PUBLISHED
        Policy a = policy("PUBLISHED");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));
        Policy after1 = service.retire(POL_ID);
        assertThat(after1.getStatus()).isEqualTo("RETIRED");
        assertThat(after1.getRetiredAt()).isNotNull();

        // From REVIEW_DUE
        Policy b = policy("REVIEW_DUE");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(b));
        Policy after2 = service.retire(POL_ID);
        assertThat(after2.getStatus()).isEqualTo("RETIRED");
    }

    @Test
    void retire_from_DRAFT_throws() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("DRAFT")));

        assertThatThrownBy(() -> service.retire(POL_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── reject ──────────────────────────────────────────────────────

    @Test
    void reject_LEGAL_REVIEW_records_reason() {
        Policy p = policy("LEGAL_REVIEW");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy after = service.reject(POL_ID, Map.of("reason", "Section 4 conflicts with PDPL Art. 12"));
        assertThat(after.getStatus()).isEqualTo("REJECTED");
        assertThat(after.getRejectionReason()).contains("PDPL");
    }

    @Test
    void reject_from_PUBLISHED_throws() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("PUBLISHED")));

        assertThatThrownBy(() -> service.reject(POL_ID, Map.of("reason", "x")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reject_with_no_reason_does_not_blow_up() {
        // Body without "reason" key → service tolerates it, just no reason stored
        Policy p = policy("EXEC_APPROVAL");
        when(repo.findById(POL_ID)).thenReturn(Optional.of(p));
        when(repo.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy after = service.reject(POL_ID, Map.of());
        assertThat(after.getStatus()).isEqualTo("REJECTED");
        assertThat(after.getRejectionReason()).isNull();
    }

    // ─── tenant scope ────────────────────────────────────────────────

    @Test
    void get_other_tenant_throws_EntityNotFound() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("DRAFT", OTHER_ORG)));

        assertThatThrownBy(() -> service.get(POL_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Policy not found");
    }

    @Test
    void executiveApprove_other_tenant_throws_EntityNotFound() {
        when(repo.findById(POL_ID)).thenReturn(Optional.of(policy("EXEC_APPROVAL", OTHER_ORG)));

        assertThatThrownBy(() -> service.executiveApprove(POL_ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(repo, never()).save(any());
    }

    // ─── stats ───────────────────────────────────────────────────────

    @Test
    void stats_aggregates_by_status() {
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "DRAFT")).thenReturn(3L);
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "LEGAL_REVIEW")).thenReturn(2L);
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "EXEC_APPROVAL")).thenReturn(1L);
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "PUBLISHED")).thenReturn(12L);
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "REVIEW_DUE")).thenReturn(4L);
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "RETIRED")).thenReturn(7L);
        when(repo.countByOrganisationIdAndStatus(ORG_ID, "REJECTED")).thenReturn(0L);

        Map<String, Long> stats = service.stats();
        assertThat(stats).containsEntry("draftCount", 3L)
                         .containsEntry("legalReviewCount", 2L)
                         .containsEntry("execApprovalCount", 1L)
                         .containsEntry("publishedCount", 12L)
                         .containsEntry("reviewDueCount", 4L)
                         .containsEntry("retiredCount", 7L)
                         .containsEntry("rejectedCount", 0L);
    }
}
