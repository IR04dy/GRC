package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.drp.entity.DisasterRecoveryPlan;
import com.tss.grc.module.bcm.drp.repository.DisasterRecoveryPlanRepository;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.repository.BcmPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Lock the BCM Continuity Readiness Score formula:
 *
 * <ul>
 *   <li>fully-populated BIA scores 100/100 across all 5 dimensions</li>
 *   <li>missing BCP costs 25 pts</li>
 *   <li>missing DRP costs 20 pts</li>
 *   <li>DR test FAILED counts as 0 on that dimension</li>
 *   <li>workaround too short (&lt;20 chars) doesn't count</li>
 *   <li>cycle aggregate averages correctly across BIAs</li>
 *   <li>leaderboard excludes DRAFT/UNDER_REVIEW BIAs</li>
 *   <li>tenant overall is 0 when no approved BIAs</li>
 *   <li>missing BIA throws EntityNotFoundException</li>
 *   <li>lowestDimension surfaces the right hint</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmReadinessScoreServiceTest {

    private static final Long ORG_ID = 101L;

    @Mock private BcmBiaAssessmentRepository bias;
    @Mock private BcmPlanRepository plans;
    @Mock private DisasterRecoveryPlanRepository drps;
    @InjectMocks private BcmReadinessScoreService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmBiaAssessment bia(String approvalStatus, Long bcpId, Long drpId,
                                         boolean workaround, String workaroundDesc) {
        BcmBiaAssessment b = new BcmBiaAssessment();
        b.setId(1L); b.setOrganisationId(ORG_ID);
        b.setBiaCode("BIA-2026-000001");
        b.setApprovalStatus(approvalStatus);
        b.setGeneratedBcpId(bcpId);
        b.setGeneratedDrpId(drpId);
        b.setManualWorkaroundAvailable(workaround);
        b.setManualWorkaroundDescription(workaroundDesc);
        b.setOverallCriticality("CRITICAL");
        return b;
    }
    private static BcmPlan bcp(String status) {
        BcmPlan p = new BcmPlan(); p.setId(10L); p.setStatus(status); return p;
    }
    private static DisasterRecoveryPlan drp(String status, String testOutcome) {
        DisasterRecoveryPlan d = new DisasterRecoveryPlan();
        d.setId(20L); d.setStatus(status); d.setLastTestedOutcome(testOutcome);
        return d;
    }

    @Test void scoreForBia_fullyPopulated_scores100() {
        BcmBiaAssessment b = bia("APPROVED", 10L, 20L, true, "Manual data entry via paper forms; takes 2x longer.");
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("APPROVED")));
        when(drps.findById(20L)).thenReturn(Optional.of(drp("APPROVED", "PASSED")));

        var sb = service.scoreForBia(1L);
        assertThat(sb.totalScore()).isEqualTo(100);
        assertThat(sb.biaApproved()).isTrue();
        assertThat(sb.bcpApproved()).isTrue();
        assertThat(sb.drpApproved()).isTrue();
        assertThat(sb.drTestPassed()).isTrue();
        assertThat(sb.manualWorkaroundDocumented()).isTrue();
        assertThat(sb.lowestDimension()).isEqualTo("complete");
    }

    @Test void scoreForBia_missingBcp_costs25() {
        BcmBiaAssessment b = bia("APPROVED", null, 20L, true, "Long enough workaround description here.");
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(drps.findById(20L)).thenReturn(Optional.of(drp("APPROVED", "PASSED")));

        var sb = service.scoreForBia(1L);
        assertThat(sb.totalScore()).isEqualTo(75);
        assertThat(sb.bcpApproved()).isFalse();
        assertThat(sb.lowestDimension()).isEqualTo("BCP not approved");
    }

    @Test void scoreForBia_missingDrp_costs40() {
        // Missing DRP costs both the 20-pt "DRP approved" dimension AND the
        // 20-pt "DR test passed" dimension (the test can't pass on a plan
        // that doesn't exist) — so 40 pts lost in total.
        BcmBiaAssessment b = bia("APPROVED", 10L, null, true, "Long enough workaround description here.");
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("APPROVED")));

        var sb = service.scoreForBia(1L);
        // 25 BIA + 25 BCP + 0 DRP + 0 DR-test + 10 workaround = 60
        assertThat(sb.totalScore()).isEqualTo(60);
        assertThat(sb.drpApproved()).isFalse();
        assertThat(sb.drTestPassed()).isFalse();
    }

    @Test void scoreForBia_drTestFailed_dimensionIsZero() {
        BcmBiaAssessment b = bia("APPROVED", 10L, 20L, true, "Long enough workaround description here.");
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("APPROVED")));
        when(drps.findById(20L)).thenReturn(Optional.of(drp("APPROVED", "FAILED")));

        var sb = service.scoreForBia(1L);
        assertThat(sb.totalScore()).isEqualTo(80);   // missing the 20-pt DR-test dimension
        assertThat(sb.drTestPassed()).isFalse();
        assertThat(sb.lowestDimension()).isEqualTo("DR test not passing");
    }

    @Test void scoreForBia_shortWorkaroundDesc_doesNotCount() {
        BcmBiaAssessment b = bia("APPROVED", 10L, 20L, true, "Too short");
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("APPROVED")));
        when(drps.findById(20L)).thenReturn(Optional.of(drp("APPROVED", "PASSED")));

        var sb = service.scoreForBia(1L);
        assertThat(sb.manualWorkaroundDocumented()).isFalse();
        assertThat(sb.totalScore()).isEqualTo(90);
    }

    @Test void scoreForBia_draftBcp_doesNotCount() {
        BcmBiaAssessment b = bia("APPROVED", 10L, null, false, null);
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("DRAFT")));

        var sb = service.scoreForBia(1L);
        assertThat(sb.bcpApproved()).isFalse();
        assertThat(sb.totalScore()).isEqualTo(25);  // BIA approved only
    }

    @Test void scoreForBia_invokedBcp_countsAsApproved() {
        BcmBiaAssessment b = bia("ACTIVE", 10L, null, false, null);
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("INVOKED")));

        var sb = service.scoreForBia(1L);
        assertThat(sb.bcpApproved()).isTrue();
        assertThat(sb.totalScore()).isEqualTo(50);
    }

    @Test void scoreForBia_unknownBia_throws() {
        when(bias.findByIdAndOrganisationId(99L, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.scoreForBia(99L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void scoreForCycle_averagesAcrossBias() {
        BcmBiaAssessment full = bia("APPROVED", 10L, 20L, true, "Long enough description here.");
        BcmBiaAssessment partial = bia("APPROVED", null, null, false, null);
        partial.setId(2L);
        when(bias.findByOrganisationIdAndCycleIdOrderByIdDesc(ORG_ID, 7L)).thenReturn(List.of(full, partial));
        when(plans.findById(10L)).thenReturn(Optional.of(bcp("APPROVED")));
        when(drps.findById(20L)).thenReturn(Optional.of(drp("APPROVED", "PASSED")));

        var cs = service.scoreForCycle(7L);
        assertThat(cs.biaCount()).isEqualTo(2);
        assertThat(cs.averageScore()).isEqualTo(62.5);  // (100 + 25) / 2
        assertThat(cs.rows()).hasSize(2);
    }

    @Test void scoreForCycle_emptyCycle_returnsZero() {
        when(bias.findByOrganisationIdAndCycleIdOrderByIdDesc(ORG_ID, 7L)).thenReturn(List.of());
        var cs = service.scoreForCycle(7L);
        assertThat(cs.biaCount()).isZero();
        assertThat(cs.averageScore()).isZero();
    }

    @Test void leaderboard_excludesDraftBias() {
        // Only APPROVED + ACTIVE rows fetched. The service queries the two statuses
        // separately, so we verify the union is what's returned.
        BcmBiaAssessment approved = bia("APPROVED", null, null, false, null);
        approved.setId(1L);
        BcmBiaAssessment active = bia("ACTIVE", null, null, false, null);
        active.setId(2L);
        when(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(ORG_ID, "APPROVED")).thenReturn(List.of(approved));
        when(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(ORG_ID, "ACTIVE")).thenReturn(List.of(active));
        // The repo for DRAFT is NOT stubbed — the service should never call it.

        var board = service.leaderboard();
        assertThat(board).hasSize(2);
    }

    @Test void tenantOverall_emptyTenant_returnsZero() {
        when(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(ORG_ID, "APPROVED")).thenReturn(List.of());
        when(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(ORG_ID, "ACTIVE")).thenReturn(List.of());
        var t = service.tenantOverall();
        assertThat(t.biaCount()).isZero();
        assertThat(t.averageScore()).isZero();
    }

    @Test void tenantOverall_buildsByCriticalityHistogram() {
        BcmBiaAssessment a = bia("APPROVED", null, null, false, null);  a.setId(1L); a.setOverallCriticality("CRITICAL");
        BcmBiaAssessment b = bia("APPROVED", null, null, false, null);  b.setId(2L); b.setOverallCriticality("HIGH");
        BcmBiaAssessment c = bia("APPROVED", null, null, false, null);  c.setId(3L); c.setOverallCriticality("CRITICAL");
        when(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(ORG_ID, "APPROVED")).thenReturn(List.of(a, b, c));
        when(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(ORG_ID, "ACTIVE")).thenReturn(List.of());

        var t = service.tenantOverall();
        assertThat(t.biaCount()).isEqualTo(3);
        assertThat(t.biasByCriticality()).containsEntry("CRITICAL", 2L).containsEntry("HIGH", 1L);
    }
}
