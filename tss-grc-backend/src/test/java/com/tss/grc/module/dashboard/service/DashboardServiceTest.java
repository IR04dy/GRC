package com.tss.grc.module.dashboard.service;

import com.tss.grc.module.compliance.repository.ComplianceAssessmentRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.dashboard.dto.DashboardStats;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainInstanceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2-407 — {@code getStats()}'s headline "open risks" KPI must count the register's
 * live (non-CLOSED) population, not only the literal {@code OPEN} initial state.
 *
 * <p>The Risk state machine (RiskService §"Risk lifecycle workflow") is
 * {@code OPEN → UNDER_REVIEW → MITIGATING → CLOSED}, {@code OPEN|UNDER_REVIEW → ACCEPTED},
 * {@code CLOSED → ARCHIVED}, {@code CLOSED|ARCHIVED → OPEN}, so UNDER_REVIEW / MITIGATING /
 * ACCEPTED / TRANSFERRED are live non-terminal states. The old
 * {@code countByStatus(org, "OPEN")} dropped every one of them, while the QUAL-BCMP-005
 * {@code openCritical}/{@code openHigh} level tiles (and the KPI trend metrics) already use
 * the single-terminal {@code != CLOSED} definition — so a CRITICAL risk being mitigated
 * raised {@code openCriticalRisks} but not {@code openRisks}, and {@code open + closed} did
 * not partition the register. This is a Mockito unit test (no Spring, no DB — F2-320/F2-325).
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final long ORG = 7L;

    @Mock private RiskRepository riskRepository;
    @Mock private UserRepository userRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private ControlRepository controlRepository;
    @Mock private ComplianceAssessmentRepository assessmentRepository;
    @Mock private ApprovalChainInstanceRepository approvalInstanceRepository;
    @Mock private EntityManager em;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor injects the six final repositories; em is a
        // @PersistenceContext field, so it is set by reflection (Mockito's
        // @InjectMocks stops after constructor injection and never field-injects it).
        service = new DashboardService(riskRepository, userRepository, incidentRepository,
                controlRepository, assessmentRepository, approvalInstanceRepository);
        ReflectionTestUtils.setField(service, "em", em);
    }

    @Test
    void openRisksCountsTheLiveNonClosedPopulationNotOnlyTheLiteralOpenState() {
        // Level counts → totalRisks = 40.
        when(riskRepository.countByOrganisationIdAndRiskLevel(ORG, "CRITICAL")).thenReturn(12L);
        when(riskRepository.countByOrganisationIdAndRiskLevel(ORG, "HIGH")).thenReturn(15L);
        when(riskRepository.countByOrganisationIdAndRiskLevel(ORG, "MEDIUM")).thenReturn(8L);
        when(riskRepository.countByOrganisationIdAndRiskLevel(ORG, "LOW")).thenReturn(5L);

        // The two counts the fix toggles between — BOTH lenient, because exactly one is
        // read per tree (pre-fix reads countByStatus(OPEN); post-fix reads
        // countByStatusNot(CLOSED)), so the control fails on the VALUE, not on
        // UnnecessaryStubbingException. 32 = 40 total - 8 closed (a real partition).
        lenient().when(riskRepository.countByOrganisationIdAndStatus(ORG, "OPEN")).thenReturn(5L);
        lenient().when(riskRepository.countByOrganisationIdAndStatusNot(ORG, "CLOSED")).thenReturn(32L);

        when(riskRepository.countByOrganisationIdAndStatus(ORG, "CLOSED")).thenReturn(8L);

        // The open-level tiles (QUAL-BCMP-005): 9 open CRITICAL is deliberately > the
        // pre-fix openRisks (5), so pre-fix openCriticalRisks (9) > openRisks (5).
        when(riskRepository.countByOrganisationIdAndRiskLevelAndStatusNot(ORG, "CRITICAL", "CLOSED")).thenReturn(9L);
        when(riskRepository.countByOrganisationIdAndRiskLevelAndStatusNot(ORG, "HIGH", "CLOSED")).thenReturn(11L);

        when(userRepository.countByOrganisationIdAndIsActiveTrue(ORG)).thenReturn(3L);
        when(userRepository.findByOrganisationId(eq(ORG), any(Pageable.class))).thenReturn(Page.empty());

        when(incidentRepository.countByOrganisationIdAndStatus(ORG, "OPEN")).thenReturn(2L);
        when(incidentRepository.countByOrganisationIdAndStatus(ORG, "INVESTIGATING")).thenReturn(1L);

        when(assessmentRepository.avgScoreByOrg(ORG)).thenReturn(80.0);

        when(approvalInstanceRepository.countByOrganisationIdAndStatusAndIsDeletedFalse(ORG, "PENDING")).thenReturn(4L);
        when(approvalInstanceRepository.countByOrganisationIdAndStatusAndIsDeletedFalse(ORG, "IN_PROGRESS")).thenReturn(1L);

        // Native overdue-audit-findings query (getStats reads it unconditionally).
        Query nativeQuery = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(0L);

        DashboardStats stats = service.getStats(ORG);

        // DISCRIMINATING: the aggregate counts the live non-CLOSED population, not the
        // literal OPEN state. Pre-fix this was 5 (countByStatus OPEN).
        assertThat(stats.openRisks()).isEqualTo(32L);

        // DISCRIMINATING (the contradiction resolved): an "open critical" is a subset of
        // "open", so openCriticalRisks can never exceed openRisks. Pre-fix 9 > 5.
        assertThat(stats.openCriticalRisks()).isLessThanOrEqualTo(stats.openRisks());

        // DISCRIMINATING (the open/closed partition): every non-closed risk is "open", so
        // open + closed = total. Pre-fix 5 + 8 = 13 != 40.
        assertThat(stats.openRisks() + stats.closedRisks()).isEqualTo(stats.totalRisks());

        // Guards (non-discriminating): the fix leaves every other field intact.
        assertThat(stats.totalRisks()).isEqualTo(40L);
        assertThat(stats.closedRisks()).isEqualTo(8L);
        assertThat(stats.criticalRisks()).isEqualTo(12L);
        assertThat(stats.openCriticalRisks()).isEqualTo(9L);
        assertThat(stats.openHighRisks()).isEqualTo(11L);
        assertThat(stats.openIncidents()).isEqualTo(3L);
        assertThat(stats.pendingTasks()).isEqualTo(5L);
    }
}
