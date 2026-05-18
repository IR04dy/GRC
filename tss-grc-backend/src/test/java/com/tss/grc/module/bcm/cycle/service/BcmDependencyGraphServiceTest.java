package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.ContinuityStrategy;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.ContinuityStrategyRepository;
import com.tss.grc.module.cyber.entity.CloudService;
import com.tss.grc.module.cyber.repository.CloudServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Lock the dependency-graph builder behaviour:
 *
 * <ul>
 *   <li>Empty tenant produces an empty graph (no NPEs)</li>
 *   <li>BIA produces a BIA node and Service/Process side nodes via soft FKs</li>
 *   <li>BCP linked to a BIA produces a covered_by edge</li>
 *   <li>BCP RTO &gt; source BIA RTO flags the node as a mismatch</li>
 *   <li>Strategy linked to BIA produces an implements edge</li>
 *   <li>CloudService node is added (disconnected — for now)</li>
 *   <li>Summary counts criticals + mismatches correctly</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmDependencyGraphServiceTest {

    private static final Long ORG_ID = 131L;

    @Mock private BcmBiaAssessmentRepository bias;
    @Mock private BcpPlanRepository plans;
    @Mock private ContinuityStrategyRepository strategies;
    @Mock private CloudServiceRepository cloudServices;
    @InjectMocks private BcmDependencyGraphService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Default to empty repositories so individual tests only stub what they care about
        when(bias.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(plans.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(strategies.findByOrganisationIdOrderByIdDesc(ORG_ID)).thenReturn(List.of());
        when(cloudServices.findByOrganisationIdOrderByServiceNameAsc(ORG_ID)).thenReturn(List.of());
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void build_emptyTenant_returnsEmptyGraph() {
        BcmDependencyGraphService.GraphPayload g = service.build();
        assertThat(g.nodes()).isEmpty();
        assertThat(g.edges()).isEmpty();
        assertThat(g.summary().nodeCount()).isZero();
    }

    @Test void build_biaProducesNodeAndServiceProcessSides() {
        BcmBiaAssessment b = new BcmBiaAssessment();
        b.setId(1L); b.setOrganisationId(ORG_ID);
        b.setBiaCode("BIA-2026-000001");
        b.setOverallCriticality("CRITICAL");
        b.setServiceId(7L); b.setProcessId(8L);
        b.setMtpdHours(24); b.setRtoHours(8); b.setRpoHours(4);
        when(bias.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(b)));

        var g = service.build();
        assertThat(g.nodes()).extracting("id").contains("bia-1", "service-7", "process-8");
        assertThat(g.edges()).extracting("relationship").contains("assesses");
        assertThat(g.summary().criticalServicesCount()).isEqualTo(1);
    }

    @Test void build_bcpWithSourceBiaProducesCoveredByEdge() {
        BcpPlan p = new BcpPlan();
        p.setId(11L); p.setOrganisationId(ORG_ID);
        p.setPlanCode("BCP-2026-000001");
        p.setPlanType("BCP"); p.setStatus("APPROVED"); p.setCurrentVersion(2);
        p.setSourceBiaId(1L); p.setRtoHours(8);
        when(plans.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(p)));

        var g = service.build();
        assertThat(g.nodes()).extracting("type").contains("BCP");
        assertThat(g.edges()).anyMatch(e -> e.relationship().equals("covered_by")
            && e.source().equals("bia-1") && e.target().equals("plan-11"));
    }

    @Test void build_planRtoExceedingBiaRto_flagsMismatch() {
        BcmBiaAssessment b = new BcmBiaAssessment();
        b.setId(1L); b.setOrganisationId(ORG_ID); b.setBiaCode("BIA-1"); b.setRtoHours(4);
        when(bias.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(b)));
        when(bias.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(b));

        BcpPlan p = new BcpPlan();
        p.setId(11L); p.setOrganisationId(ORG_ID); p.setPlanCode("BCP-1");
        p.setPlanType("BCP"); p.setStatus("APPROVED"); p.setCurrentVersion(1);
        p.setSourceBiaId(1L); p.setRtoHours(48);  // exceeds the BIA's 4h
        when(plans.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(p)));

        var g = service.build();
        var planNode = g.nodes().stream().filter(n -> "plan-11".equals(n.id())).findFirst().orElseThrow();
        assertThat(planNode.data().get("rto_mismatch_vs_bia")).isEqualTo(true);
        assertThat(g.summary().rtoMismatchesCount()).isEqualTo(1);
    }

    @Test void build_strategyLinkedToBia_producesImplementsEdge() {
        ContinuityStrategy s = new ContinuityStrategy();
        s.setId(5L); s.setOrganisationId(ORG_ID);
        s.setSelectedStrategy("ACTIVE_PASSIVE");
        s.setApprovalStatus("APPROVED");
        s.setRelatedBiaId(1L);
        when(strategies.findByOrganisationIdOrderByIdDesc(ORG_ID)).thenReturn(List.of(s));

        var g = service.build();
        assertThat(g.nodes()).anyMatch(n -> "strategy-5".equals(n.id()) && "STRATEGY".equals(n.type()));
        assertThat(g.edges()).anyMatch(e -> "implements".equals(e.relationship())
            && "bia-1".equals(e.source()) && "strategy-5".equals(e.target()));
    }

    @Test void build_cloudServiceNodeAdded() {
        CloudService c = new CloudService();
        c.setId(3L); c.setOrganisationId(ORG_ID);
        c.setServiceName("AWS Riyadh");
        c.setProvider("AWS"); c.setCriticality("CRITICAL");
        c.setKingdomDataResident(true);
        when(cloudServices.findByOrganisationIdOrderByServiceNameAsc(ORG_ID)).thenReturn(List.of(c));

        var g = service.build();
        assertThat(g.nodes()).anyMatch(n -> "cloud-3".equals(n.id()) && "CLOUD".equals(n.type()));
        assertThat(g.summary().nodesByType()).containsEntry("CLOUD", 1L);
    }

    @Test void build_summaryAggregatesNodeTypesAndCounts() {
        BcmBiaAssessment b = new BcmBiaAssessment();
        b.setId(1L); b.setOrganisationId(ORG_ID); b.setBiaCode("X"); b.setOverallCriticality("CRITICAL");
        b.setServiceId(7L);
        when(bias.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(b)));

        var summary = service.build().summary();
        assertThat(summary.nodesByType()).containsKeys("BIA", "SERVICE");
        assertThat(summary.criticalServicesCount()).isEqualTo(1);
    }
}
