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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * BCM Dependency Graph Builder — audit Tier-2.
 *
 * <p>Builds an interactive graph showing the relationships
 * BIA → service / process → BCP/DRP → continuity strategy → cloud service
 * → supplier for the current tenant. Replaces the mock-data tree on
 * {@code /bcm-dependency} with a real graph fed by entities that already exist.
 *
 * <p>Returns a typed payload that maps cleanly to react-flow's expected
 * {nodes, edges} structure on the frontend. Each node carries a label, a
 * type discriminator (BIA / SERVICE / PROCESS / BCP / DRP / STRATEGY /
 * CLOUD / SUPPLIER), and optional metadata (criticality, RTO, status) so
 * the UI can colour-overlay without extra round trips.
 *
 * <h3>Why this shape</h3>
 * <p>The audit (§4 row 1) called out that customers demo the dependency
 * graph first. The pragmatic shape is: nodes typed by entity kind so the
 * frontend can render different shapes / colours per type, and edges with
 * a relationship label ({@code "implements"}, {@code "covers"},
 * {@code "depends_on"}, etc.) so the UI can show what the line means on hover.
 */
@Service
@RequiredArgsConstructor
public class BcmDependencyGraphService {

    public record Node(String id, String type, String label, Map<String, Object> data) {}
    public record Edge(String id, String source, String target, String relationship) {}
    public record GraphPayload(List<Node> nodes, List<Edge> edges, GraphSummary summary) {}
    public record GraphSummary(int nodeCount, int edgeCount, Map<String, Long> nodesByType,
                                long criticalServicesCount, long rtoMismatchesCount) {}

    private final BcmBiaAssessmentRepository bias;
    private final BcpPlanRepository plans;
    private final ContinuityStrategyRepository strategies;
    private final CloudServiceRepository cloudServices;

    /**
     * Build the full tenant-scoped graph. Nodes and edges are deduplicated
     * by id — pure structural data, no rendering hints (those are derived
     * frontend-side from the {@code type} discriminator + {@code data} map).
     */
    @Transactional(readOnly = true)
    public GraphPayload build() {
        Long org = TenantContext.getCurrentTenant();

        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<String, Edge> edges = new LinkedHashMap<>();
        Map<String, Long> nodesByType = new LinkedHashMap<>();
        // PERF-NQ-006: index the BIAs we already load below so the per-plan
        // RTO-mismatch check resolves from memory instead of issuing a
        // findByIdAndOrganisationId query for every plan.
        Map<Long, BcmBiaAssessment> biaById = new HashMap<>();
        long rtoMismatches = 0;
        long criticalServices = 0;

        // BIAs — anchor of the graph. Each BIA links service + process + RTO/RPO/MTPD.
        List<BcmBiaAssessment> biaRows = bias.findByOrganisationIdOrderByIdDesc(
                org, PageRequest.of(0, 1000)).getContent();
        for (BcmBiaAssessment b : biaRows) {
            biaById.put(b.getId(), b);
            String biaNodeId = "bia-" + b.getId();
            nodes.put(biaNodeId, new Node(biaNodeId, "BIA", b.getBiaCode(),
                Map.of(
                    "criticality", String.valueOf(b.getOverallCriticality()),
                    "mtpd_h",      b.getMtpdHours() == null ? -1 : b.getMtpdHours(),
                    "rto_h",       b.getRtoHours() == null ? -1 : b.getRtoHours(),
                    "rpo_h",       b.getRpoHours() == null ? -1 : b.getRpoHours(),
                    "status",      String.valueOf(b.getApprovalStatus())
                )));
            if ("CRITICAL".equals(b.getOverallCriticality()) || "SOVEREIGN".equals(b.getOverallCriticality())) {
                criticalServices++;
            }

            // Service node + edge
            if (b.getServiceId() != null) {
                String svc = "service-" + b.getServiceId();
                nodes.putIfAbsent(svc, new Node(svc, "SERVICE",
                    "Service #" + b.getServiceId(),
                    Map.of("source", "soft_fk_from_bia")));
                addEdge(edges, biaNodeId, svc, "assesses");
            }
            // Process node + edge
            if (b.getProcessId() != null) {
                String pr = "process-" + b.getProcessId();
                nodes.putIfAbsent(pr, new Node(pr, "PROCESS",
                    "Process #" + b.getProcessId(),
                    Map.of("source", "soft_fk_from_bia")));
                addEdge(edges, biaNodeId, pr, "assesses");
            }
        }

        // BCP / DRP plans — pull together via source_bia_id
        for (BcpPlan p : plans.findByOrganisationIdOrderByIdDesc(org, PageRequest.of(0, 1000)).getContent()) {
            String planNodeId = "plan-" + p.getId();
            nodes.put(planNodeId, new Node(planNodeId, p.getPlanType(), p.getPlanCode(),
                Map.of(
                    "title",    nullToEmpty(p.getTitleEn()),
                    "status",   p.getStatus(),
                    "version",  p.getCurrentVersion(),
                    "rto_h",    p.getRtoHours() == null ? -1 : p.getRtoHours()
                )));
            if (p.getSourceBiaId() != null) {
                addEdge(edges, "bia-" + p.getSourceBiaId(), planNodeId, "covered_by");
            }
            if (p.getServiceId() != null) {
                String svc = "service-" + p.getServiceId();
                nodes.putIfAbsent(svc, new Node(svc, "SERVICE",
                    "Service #" + p.getServiceId(),
                    Map.of("source", "soft_fk_from_plan")));
                addEdge(edges, svc, planNodeId, "covered_by");
            }
            // RTO mismatch detection: plan's RTO must be ≤ BIA's RTO.
            // PERF-NQ-006: resolve the source BIA from the in-memory map built
            // above rather than re-querying per plan. (The map is scoped to the
            // same BIA set that produced the graph's bia- nodes, so the mismatch
            // tag always references a node that exists in the payload.)
            if (p.getSourceBiaId() != null && p.getRtoHours() != null) {
                BcmBiaAssessment srcBia = biaById.get(p.getSourceBiaId());
                if (srcBia != null && srcBia.getRtoHours() != null && p.getRtoHours() > srcBia.getRtoHours()) {
                    // Tag the plan node — UI overlays this as a warning indicator
                    Node existing = nodes.get(planNodeId);
                    Map<String, Object> data = new HashMap<>(existing.data());
                    data.put("rto_mismatch_vs_bia", true);
                    nodes.put(planNodeId, new Node(existing.id(), existing.type(), existing.label(), data));
                }
                // Recount in a separate pass below so we don't double-count
            }
        }
        // Second pass to count mismatches (closure-friendly final variable)
        for (Node n : nodes.values()) {
            if (Boolean.TRUE.equals(n.data().get("rto_mismatch_vs_bia"))) rtoMismatches++;
        }

        // Continuity strategies — connect to the BIA they reference and tag with strategy type
        for (ContinuityStrategy s : strategies.findByOrganisationIdOrderByIdDesc(org)) {
            String stratId = "strategy-" + s.getId();
            nodes.put(stratId, new Node(stratId, "STRATEGY",
                s.getSelectedStrategy() == null ? "Strategy #" + s.getId() : s.getSelectedStrategy(),
                Map.of(
                    "approval", nullToEmpty(s.getApprovalStatus()),
                    "rto_req",  s.getRtoRequirementHours() == null ? -1 : s.getRtoRequirementHours(),
                    "rpo_req",  s.getRpoRequirementHours() == null ? -1 : s.getRpoRequirementHours()
                )));
            if (s.getRelatedBiaId() != null) {
                addEdge(edges, "bia-" + s.getRelatedBiaId(), stratId, "implements");
            }
            if (s.getRelatedServiceId() != null) {
                String svc = "service-" + s.getRelatedServiceId();
                nodes.putIfAbsent(svc, new Node(svc, "SERVICE",
                    "Service #" + s.getRelatedServiceId(),
                    Map.of("source", "soft_fk_from_strategy")));
                addEdge(edges, svc, stratId, "protected_by");
            }
        }

        // Cloud services — show as dependency nodes; edge to any BIA that
        // references them via affected_systems is a future enhancement.
        for (CloudService c : cloudServices.findByOrganisationIdOrderByServiceNameAsc(org)) {
            String cloudId = "cloud-" + c.getId();
            nodes.put(cloudId, new Node(cloudId, "CLOUD",
                c.getServiceName(),
                Map.of(
                    "provider",     nullToEmpty(c.getProvider()),
                    "criticality",  nullToEmpty(c.getCriticality()),
                    "model",        nullToEmpty(c.getServiceModel()),
                    "ksa_resident", c.getKingdomDataResident()
                )));
            // No service-level FK on CloudService yet — left disconnected.
            // Future sprint: add cloud_service_id mapping table or annotate
            // CriticalService entity with required cloud services.
        }

        // Build the type histogram
        for (Node n : nodes.values()) {
            nodesByType.merge(n.type(), 1L, Long::sum);
        }

        GraphSummary summary = new GraphSummary(
            nodes.size(), edges.size(), nodesByType,
            criticalServices, rtoMismatches);
        return new GraphPayload(new ArrayList<>(nodes.values()),
                                 new ArrayList<>(edges.values()), summary);
    }

    private static void addEdge(Map<String, Edge> edges, String source, String target, String relationship) {
        // Deterministic id so the frontend can stable-key on it
        String id = "e-" + source + "->" + target;
        edges.putIfAbsent(id, new Edge(id, source, target, relationship));
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
