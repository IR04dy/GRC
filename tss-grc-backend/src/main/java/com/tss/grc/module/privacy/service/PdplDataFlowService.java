package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PDPL data-flow visualisation service — Sprint-8 Slice 8-C.
 *
 * <p>Builds a node-link graph from the existing
 * {@code privacy.processing_activities} (RoPA) rows so the DPO can
 * see, in one screen, where personal data flows: from data category
 * → activity → cross-border destination + transfer mechanism.
 *
 * <p>Graph shape (Saudi-native bilingual labels carried through):
 *
 * <pre>
 *   nodes:
 *     [CATEGORY:HEALTH]          (data category)
 *     [ACTIVITY:Patient billing] (RoPA row)
 *     [COUNTRY:GBR]              (destination jurisdiction)
 *     [MECHANISM:SCC]            (transfer mechanism)
 *
 *   edges:
 *     CATEGORY → ACTIVITY        (processing activity uses this category)
 *     ACTIVITY → COUNTRY         (cross-border destination)
 *     ACTIVITY → MECHANISM       (transfer mechanism in place)
 * </pre>
 *
 * <p>Pure read aggregator — no schema changes. Reads
 * {@code processing_activities} and parses its {@code data_categories_json}
 * blob for the category set.
 *
 * <p>Tenant-scoped via {@link TenantContext}.
 */
@Service
@RequiredArgsConstructor
public class PdplDataFlowService {

    private final ProcessingActivityRepository activities;

    @Transactional(readOnly = true)
    public DataFlowGraph buildGraph() {
        Long orgId = TenantContext.getCurrentTenant();
        List<ProcessingActivity> rows = activities
            .findByOrganisationId(orgId, Pageable.unpaged())
            .getContent();

        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        int totalCategoryEdges = 0;
        int crossBorderActivities = 0;
        Set<String> destinations = new TreeSet<>();
        Map<String, Long> categoryFreq = new TreeMap<>();
        Map<String, Long> mechanismFreq = new LinkedHashMap<>();

        for (ProcessingActivity a : rows) {
            // 1. Activity node
            String aId = "ACTIVITY:" + a.getId();
            putNode(nodes, aId, "ACTIVITY",
                a.getActivityNameEn(), a.getActivityNameAr(),
                Map.of(
                    "legalBasis",            nz(a.getLegalBasis()),
                    "status",                nz(a.getStatus()),
                    "specialCategoryFlag",   String.valueOf(Boolean.TRUE.equals(a.getSpecialCategoryFlag())),
                    "retentionPeriodMonths", a.getRetentionPeriodMonths() == null
                                              ? "" : a.getRetentionPeriodMonths().toString()
                ));

            // 2. Category nodes + edges
            for (String cat : parseCategories(a.getDataCategoriesJson())) {
                String cId = "CATEGORY:" + cat;
                putNode(nodes, cId, "CATEGORY", cat, categoryLabelAr(cat), Map.of());
                edges.add(new Edge(cId, aId, "USED_BY", null));
                categoryFreq.merge(cat, 1L, Long::sum);
                totalCategoryEdges++;
            }

            // 3. Cross-border edges
            if (Boolean.TRUE.equals(a.getCrossBorderTransfer())) {
                crossBorderActivities++;
                String country = nzDefault(a.getReceivingCountry(), "UNKNOWN");
                String coId = "COUNTRY:" + country;
                putNode(nodes, coId, "COUNTRY", country, country,
                    Map.of("adequacy", nzDefault(a.getAdequacyDecision(), "UNKNOWN")));
                destinations.add(country);
                edges.add(new Edge(aId, coId, "TRANSFERS_TO",
                    nzDefault(a.getAdequacyDecision(), "UNKNOWN")));

                String mech = nzDefault(a.getTransferMechanism(), "UNDOCUMENTED");
                String mId = "MECHANISM:" + mech;
                putNode(nodes, mId, "MECHANISM", mech, mechanismLabelAr(mech), Map.of());
                edges.add(new Edge(aId, mId, "GOVERNED_BY", null));
                mechanismFreq.merge(mech, 1L, Long::sum);
            }
        }

        Map<String, Long> nodeTypeCount = new LinkedHashMap<>();
        nodeTypeCount.put("ACTIVITY", 0L);
        nodeTypeCount.put("CATEGORY", 0L);
        nodeTypeCount.put("COUNTRY",  0L);
        nodeTypeCount.put("MECHANISM",0L);
        for (Node n : nodes.values()) nodeTypeCount.merge(n.type(), 1L, Long::sum);

        return new DataFlowGraph(
            new ArrayList<>(nodes.values()),
            edges,
            new GraphSummary(
                rows.size(), crossBorderActivities, destinations.size(),
                totalCategoryEdges, nodeTypeCount, categoryFreq, mechanismFreq
            )
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static void putNode(Map<String, Node> map, String id, String type,
                                String labelEn, String labelAr, Map<String, String> attrs) {
        map.putIfAbsent(id, new Node(id, type, labelEn, labelAr, attrs));
    }

    private static String nz(String s)               { return s == null ? "" : s; }
    private static String nzDefault(String s, String d) { return (s == null || s.isBlank()) ? d : s; }

    /** Pull bare strings out of {@code ["HEALTH","BIOMETRIC", ...]} without a JSON parser. */
    private static final Pattern TOKEN = Pattern.compile("\"([A-Z_][A-Z0-9_]*)\"");
    private static Set<String> parseCategories(String json) {
        if (json == null || json.isBlank()) return Set.of();
        Set<String> out = new TreeSet<>();
        Matcher m = TOKEN.matcher(json);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String categoryLabelAr(String cat) {
        return switch (cat) {
            case "HEALTH"             -> "بيانات صحية";
            case "BIOMETRIC"          -> "بيانات حيوية";
            case "GENETIC"            -> "بيانات وراثية";
            case "FINANCIAL"          -> "بيانات مالية";
            case "CHILDREN_UNDER_18"  -> "بيانات قاصرين";
            case "LOCATION"           -> "بيانات موقع";
            case "RELIGIOUS"          -> "بيانات دينية";
            case "POLITICAL"          -> "بيانات سياسية";
            case "CRIMINAL"           -> "بيانات جنائية";
            case "IDENTITY"           -> "بيانات هوية";
            case "CONTACT"            -> "بيانات اتصال";
            default                   -> cat;
        };
    }

    private static String mechanismLabelAr(String mech) {
        return switch (mech) {
            case "SCC"                  -> "بنود تعاقدية معيارية";
            case "BCR"                  -> "قواعد ملزِمة للشركة";
            case "EXPLICIT_CONSENT"     -> "موافقة صريحة";
            case "DATA_OFFICE_APPROVAL" -> "موافقة مكتب البيانات";
            case "INTRA_GROUP"          -> "تحويل داخل المجموعة";
            case "UNDOCUMENTED"         -> "غير موثق";
            default                     -> mech;
        };
    }

    // ─── DTO types ───────────────────────────────────────────────────

    public record DataFlowGraph(
        List<Node> nodes,
        List<Edge> edges,
        GraphSummary summary
    ) {}

    /** Node — `type` is one of ACTIVITY / CATEGORY / COUNTRY / MECHANISM. */
    public record Node(
        String id,                    // typed: "ACTIVITY:7", "CATEGORY:HEALTH", ...
        String type,
        String labelEn,
        String labelAr,
        Map<String, String> attrs     // free-form
    ) {}

    /** Edge — `relation` is one of USED_BY / TRANSFERS_TO / GOVERNED_BY. */
    public record Edge(
        String source,
        String target,
        String relation,
        String label                  // adequacy tag for TRANSFERS_TO; null otherwise
    ) {}

    public record GraphSummary(
        int activityCount,
        int crossBorderActivityCount,
        int distinctDestinations,
        int categoryEdgeCount,
        Map<String, Long> nodesByType,
        Map<String, Long> categoryFrequency,
        Map<String, Long> mechanismFrequency
    ) {}
}
