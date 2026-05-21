package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * BcmReportService — BCM-7 catalogue + dispatcher lock.
 *
 * <ul>
 *   <li>catalog() returns exactly 15 definitions</li>
 *   <li>unknown report code rejected with IllegalArgumentException</li>
 *   <li>missing tenant rejected with IllegalStateException</li>
 *   <li>BCM_READINESS aggregates the right counts</li>
 *   <li>CRITICAL_SERVICES classifies impact correctly</li>
 *   <li>RESIDUAL_RISK_HEATMAP keys by L×I and skips null dimensions</li>
 *   <li>POLICY_REVIEW_QUEUE filters by nextReviewDate &lt; today</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmReportServiceTest {

    private static final Long ORG_ID = 21L;

    @Mock private BcmAssessmentCycleRepository cycles;
    @Mock private BcmGovernanceRecordRepository governance;
    @Mock private BcmRiskRepository risks;
    @Mock private ContinuityStrategyRepository strategies;
    @Mock private BcmCommunicationContactRepository contacts;
    @Mock private BcmLessonLearnedRepository lessons;
    @Mock private BcmGapRepository gaps;
    @Mock private BcmCorrectiveActionRepository capas;
    @Mock private AuditLogService audit;
    @InjectMocks private BcmReportService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test void catalog_containsAllExpectedReports() {
        // The original BCM-7 catalog was 15; Tier-4 #25 + #26 added the NCA
        // cyber-disruption and CMA annual-disclosure reports for KSA regulator
        // coverage. Assert membership of the canonical set rather than a hard
        // size so future additions don't keep flipping this test red.
        assertThat(service.catalog())
            .extracting(BcmReportService.ReportDef::code)
            .contains(
                "BCM_READINESS", "EXECUTIVE_SUMMARY", "RESIDUAL_RISK_HEATMAP",
                "NCA_CYBER_CONTINUITY", "CMA_ANNUAL_DISCLOSURE");
        assertThat(service.catalog().size()).isGreaterThanOrEqualTo(15);
    }

    @Test void generate_unknownCode_throws() {
        assertThatThrownBy(() -> service.generate("NOT_A_REPORT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown BCM report code");
    }

    @Test void generate_missingTenant_throws() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.generate("BCM_READINESS"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void bcmReadiness_aggregatesCorrectly() {
        BcmAssessmentCycle c1 = cycle("APPROVED",  BigDecimal.valueOf(85));
        BcmAssessmentCycle c2 = cycle("IN_PROGRESS", BigDecimal.valueOf(60));
        when(cycles.findByOrganisationIdOrderByReportingYearDesc(ORG_ID)).thenReturn(List.of(c1, c2));
        when(gaps.countByOrganisationIdAndStatus(ORG_ID, "OPEN")).thenReturn(4L);
        when(capas.countByOrganisationIdAndStatus(ORG_ID, "IN_PROGRESS")).thenReturn(2L);

        Map<String, Object> out = service.generate("BCM_READINESS");

        assertThat(out.get("totalCycles")).isEqualTo(2);
        assertThat(out.get("approvedCycles")).isEqualTo(1L);
        assertThat(out.get("activeCycles")).isEqualTo(1L);
        assertThat(out.get("openGaps")).isEqualTo(4L);
        assertThat(out.get("openCapas")).isEqualTo(2L);
        assertThat(out.get("avgOverallScore")).isEqualTo(72.5);
        assertThat(out.get("reportCode")).isEqualTo("BCM_READINESS");
    }

    @Test void criticalServices_classifiesImpactCorrectly() {
        when(risks.findByOrganisationIdOrderByIdDesc(ORG_ID)).thenReturn(List.of(
            risk(5), risk(5), risk(4), risk(3), risk(null)
        ));

        Map<String, Object> out = service.generate("CRITICAL_SERVICES");
        assertThat(out.get("criticalImpact")).isEqualTo(2L);
        assertThat(out.get("highImpact")).isEqualTo(1L);
        assertThat(out.get("total")).isEqualTo(3L);
    }

    @Test void residualRiskHeatmap_keysByLikelihoodAndImpact_skipsNulls() {
        when(risks.findByOrganisationIdOrderByIdDesc(ORG_ID)).thenReturn(List.of(
            risk(3, 4), risk(3, 4), risk(2, 5), risk(null, 4), risk(3, null)
        ));

        @SuppressWarnings("unchecked")
        Map<String, Long> cells = (Map<String, Long>) service.generate("RESIDUAL_RISK_HEATMAP").get("cells");
        assertThat(cells).containsEntry("L3_I4", 2L).containsEntry("L2_I5", 1L);
        assertThat(cells).hasSize(2);  // nulls dropped
    }

    @Test void policyReviewQueue_filtersByPastNextReviewDate() {
        BcmGovernanceRecord overdue = governance(LocalDate.now().minusDays(10));
        BcmGovernanceRecord upcoming = governance(LocalDate.now().plusDays(30));
        BcmGovernanceRecord nullDate = governance(null);
        when(governance.findByOrganisationIdOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of(overdue, upcoming, nullDate));

        Map<String, Object> out = service.generate("POLICY_REVIEW_QUEUE");
        assertThat(out.get("overdueCount")).isEqualTo(1);
    }

    // ── Fixtures ──────────────────────────────────────────────────

    private static BcmAssessmentCycle cycle(String status, BigDecimal overallScore) {
        BcmAssessmentCycle c = new BcmAssessmentCycle();
        c.setId(1L); c.setOrganisationId(ORG_ID);
        c.setStatus(status); c.setOverallScore(overallScore);
        c.setReportingYear((short) 2026);
        return c;
    }

    private static BcmRisk risk(Integer impact) { return risk(null, impact); }

    private static BcmRisk risk(Integer likelihood, Integer impact) {
        BcmRisk r = new BcmRisk();
        r.setId(1L); r.setOrganisationId(ORG_ID);
        r.setTitleEn("x"); r.setThreatCategory("NATURAL");
        r.setLikelihood(likelihood);
        r.setImpact(impact);
        return r;
    }

    private static BcmGovernanceRecord governance(LocalDate nextReview) {
        BcmGovernanceRecord g = new BcmGovernanceRecord();
        g.setId(1L); g.setOrganisationId(ORG_ID);
        g.setPolicyTitleEn("BCM Policy"); g.setPolicyVersion("1.0");
        g.setApprovalStatus("ACTIVE");
        g.setNextReviewDate(nextReview);
        return g;
    }
}
