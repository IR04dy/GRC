package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-336 — <b>"open" corrective actions / gaps must be the COMPLEMENT of the resolved set, never a
 * single inclusion token.</b>
 *
 * <p>The BCM CAPA machine has eight states, terminal {@code = {CLOSED}}, so seven are open — OPEN
 * (the initial state), ASSIGNED, IN_PROGRESS, EVIDENCE_SUBMITTED, UNDER_REVIEW, <b>OVERDUE</b> and
 * REOPENED. Yet the NCA/ISO 22301 {@code BCM_READINESS} report counted {@code openCapas} as only
 * {@code IN_PROGRESS}, the CMA annual disclosure the same, and the dashboard {@code stats()} as only
 * {@code OPEN} — three readers of one concept, three different single tokens, all under-counting in
 * the flattering direction (an OVERDUE corrective action and a freshly-created OPEN one were invisible
 * on a readiness KPI). Gaps were consistently {@code {OPEN}}, dropping the four actively-worked states.
 * {@link BcmBoardPackService#capaSnapshot} was the one authoritative reader (its {@code activeOpen}
 * sums all seven non-CLOSED; its overdue sweep uses {@code done = List.of("CLOSED")}).
 *
 * <p>F2-196's rule (open = complement of a declared terminal set) with F2-282's follow-up (a concept
 * read by several places must be read the same way): the resolved sets now live once on
 * {@link BcmRemediationService} and every reader references them.
 *
 * <p>Pure read-path logic, fully visible to a mocked repository — no DB, tenant, row_version or
 * collation dimension — so verified hermetically (the F2-320/F2-325 calibration standard). The
 * discriminating legs stub the complement count to a sentinel and prove the reader returns it AND
 * never calls the old single-token count.
 */
@ExtendWith(MockitoExtension.class)
class BcmOpenRemediationCountTest {

    private static final Long ORG = 7L;

    @Mock private BcmAssessmentCycleRepository cycles;
    @Mock private BcmGovernanceRecordRepository governance;
    @Mock private BcmRiskRepository risks;
    @Mock private ContinuityStrategyRepository strategies;
    @Mock private BcmCommunicationContactRepository contacts;
    @Mock private BcmLessonLearnedRepository lessons;
    @Mock private BcmGapRepository gaps;
    @Mock private BcmCorrectiveActionRepository capas;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private BcmReportService reportService;
    @InjectMocks private BcmRemediationService remediationService;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── behavioural: every reader counts the complement, not a single token ──────────────────────

    @Test void bcmReadiness_countsOpenAsTheComplementNotASingleToken() {
        when(cycles.findByOrganisationIdOrderByReportingYearDesc(ORG)).thenReturn(List.of());
        when(gaps.countByOrganisationIdAndStatusNotIn(ORG, BcmRemediationService.GAP_RESOLVED)).thenReturn(42L);
        when(capas.countByOrganisationIdAndStatusNotIn(ORG, BcmRemediationService.CAPA_RESOLVED)).thenReturn(17L);

        Map<String, Object> out = reportService.generate("BCM_READINESS");

        assertThat(out.get("openGaps")).isEqualTo(42L);
        assertThat(out.get("openCapas")).isEqualTo(17L);
        // A revert to a single-token count would return 0 here (unstubbed) AND fail these verifies.
        verify(gaps, never()).countByOrganisationIdAndStatus(eq(ORG), eq("OPEN"));
        verify(capas, never()).countByOrganisationIdAndStatus(eq(ORG), eq("IN_PROGRESS"));
    }

    @Test void cmaAnnualDisclosure_countsOpenAsTheComplement() {
        when(cycles.findByOrganisationIdOrderByReportingYearDesc(ORG)).thenReturn(List.of());
        when(governance.findByOrganisationIdOrderByCreatedAtDesc(ORG)).thenReturn(List.of());
        when(gaps.countByOrganisationIdAndStatusNotIn(ORG, BcmRemediationService.GAP_RESOLVED)).thenReturn(9L);
        when(capas.countByOrganisationIdAndStatusNotIn(ORG, BcmRemediationService.CAPA_RESOLVED)).thenReturn(5L);

        Map<String, Object> out = reportService.generate("CMA_ANNUAL_DISCLOSURE");

        assertThat(out.get("openMaterialGaps")).isEqualTo(9L);
        assertThat(out.get("openCorrectiveActions")).isEqualTo(5L);
        verify(gaps, never()).countByOrganisationIdAndStatus(eq(ORG), eq("OPEN"));
        verify(capas, never()).countByOrganisationIdAndStatus(eq(ORG), eq("IN_PROGRESS"));
    }

    @Test void remediationStats_countsOpenAsTheComplement() {
        when(gaps.countByOrganisationIdAndStatusNotIn(ORG, BcmRemediationService.GAP_RESOLVED)).thenReturn(8L);
        when(gaps.countByOrganisationIdAndSeverity(ORG, "CRITICAL")).thenReturn(3L);
        when(capas.countByOrganisationIdAndStatusNotIn(ORG, BcmRemediationService.CAPA_RESOLVED)).thenReturn(6L);
        when(capas.countByOrganisationIdAndStatus(ORG, "OVERDUE")).thenReturn(2L);
        when(capas.countByOrganisationIdAndStatus(ORG, "CLOSED")).thenReturn(10L);

        Map<String, Long> out = remediationService.stats();

        assertThat(out.get("openGaps")).isEqualTo(8L);
        assertThat(out.get("openCapas")).isEqualTo(6L);
        // OVERDUE/CLOSED are still counted as their own figures; only the OPEN-labelled figure changed.
        verify(gaps, never()).countByOrganisationIdAndStatus(eq(ORG), eq("OPEN"));
        verify(capas, never()).countByOrganisationIdAndStatus(eq(ORG), eq("OPEN"));
        verify(capas, never()).countByOrganisationIdAndStatus(eq(ORG), eq("IN_PROGRESS"));
    }

    // ── grounding: the resolved sets are the terminal dispositions of the real vocabulary ────────

    @Test void resolvedSetsAreExactlyTheTerminalDispositions() throws Exception {
        // A CAPA is resolved only by CLOSED (the machine's sole no-outgoing-edge state); every other
        // state — including OVERDUE and the initial OPEN — is open.
        assertThat(BcmRemediationService.CAPA_RESOLVED).containsExactlyInAnyOrder("CLOSED");
        assertThat(BcmRemediationService.CAPA_RESOLVED).doesNotContain("OVERDUE", "OPEN", "IN_PROGRESS");

        // A gap is resolved either by remediation (CLOSED) or by formally accepting the residual risk
        // (ACCEPTED_RISK) — the platform's own treatment (ASM CLOSING_STATUSES, NDI critical-open).
        assertThat(BcmRemediationService.GAP_RESOLVED).containsExactlyInAnyOrder("CLOSED", "ACCEPTED_RISK");
        assertThat(BcmRemediationService.GAP_RESOLVED)
            .doesNotContain("OPEN", "ASSIGNED", "IN_PROGRESS", "UNDER_REVIEW", "REOPENED");

        // Anti-drift: every resolved token is a real member of the register's declared vocabulary, so
        // renaming a state in the machine cannot leave the resolved set silently pointing at nothing.
        assertThat(privateVocabulary("CAPA_STATUSES")).containsAll(BcmRemediationService.CAPA_RESOLVED);
        assertThat(privateVocabulary("GAP_STATUSES")).containsAll(BcmRemediationService.GAP_RESOLVED);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> privateVocabulary(String field) throws Exception {
        Field f = BcmRemediationService.class.getDeclaredField(field);
        f.setAccessible(true);
        return (Set<String>) f.get(null);
    }
}
