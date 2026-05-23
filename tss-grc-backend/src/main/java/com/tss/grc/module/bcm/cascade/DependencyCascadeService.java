package com.tss.grc.module.bcm.cascade;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.entity.ProcessDependency;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.bcm.repository.ProcessDependencyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Dependency-cascade simulator — Sprint-9 Slice 9-A.
 *
 * <p>Given a single critical process failure, compute the downstream impact
 * at standard BCM time-bands (1h / 4h / 8h / 24h). Reads
 * {@code bcm.process_dependencies}, {@code bcm.critical_processes}, and
 * {@code bcm.bia_records}.
 *
 * <p>Saudi BCM Managers use this for "what-if" planning before exercises and
 * during real crises: "if our T24 core banking platform fails at 09:00,
 * which downstream services breach SLA by 13:00? Which ones SAMA cares
 * about?"
 *
 * <h3>Algorithm</h3>
 *
 * 1. Build a reverse adjacency map: {@code upstreamProcessId → [consumers]}.
 * 2. BFS from the failed process. Track depth from the source.
 * 3. For each reached process, look up the lowest MTPD/RTO from
 *    {@code BiaRecord.findByProcessId(...)} (use first-found ordering).
 * 4. Classify reach time by depth-weighted heuristic:
 *    direct dependents = 1h, second-level = 4h, third-level = 8h,
 *    deeper = 24h. (No latency data on edges yet — Slice 9-A is the v1
 *    cascade. A future slice can add per-edge propagation delays.)
 * 5. A process is {@code breached} when {@code reachHour >= mtpdHours}
 *    (or RTO when MTPD missing). Confidence is 100% for level-1,
 *    75% for level-2, 50% for level-3, 25% for deeper.
 *
 * <p>Pure read aggregator — no schema changes. Tenant-scoped via
 * {@link TenantContext}.
 */
@Service
@RequiredArgsConstructor
public class DependencyCascadeService {

    /** Maximum BFS depth — beyond this the cascade signal is noise. */
    public static final int MAX_DEPTH = 6;

    /** Time-band hours we report against (BCM-standard buckets). */
    public static final int[] HOUR_BANDS = { 1, 4, 8, 24 };

    private final CriticalProcessRepository processes;
    private final ProcessDependencyRepository deps;
    private final BiaRecordRepository bias;

    // ─── Public surface ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CascadeResult simulate(Long failedProcessId) {
        Long org = TenantContext.getCurrentTenant();

        List<CriticalProcess> all = processes.findByOrganisationId(org);
        Map<Long, CriticalProcess> processById = new HashMap<>();
        for (CriticalProcess p : all) processById.put(p.getId(), p);
        if (!processById.containsKey(failedProcessId)) {
            throw new EntityNotFoundException(
                "Critical process not found in tenant: " + failedProcessId);
        }

        // BIAs per process — pick the most-conservative MTPD/RTO per process.
        Map<Long, BiaRecord> biaByProcess = new HashMap<>();
        for (BiaRecord b : bias.findByProcessIdIn(new ArrayList<>(processById.keySet()))) {
            BiaRecord cur = biaByProcess.get(b.getProcessId());
            if (cur == null
                || (b.getMtpdHours() != null
                    && (cur.getMtpdHours() == null
                        || b.getMtpdHours() < cur.getMtpdHours()))) {
                biaByProcess.put(b.getProcessId(), b);
            }
        }

        // Reverse adjacency: upstream → [direct consumers]
        Map<Long, List<ProcessDependency>> consumersOf = new HashMap<>();
        for (ProcessDependency d : deps.findByOrganisationId(org)) {
            consumersOf.computeIfAbsent(d.getDependsOnProcessId(), k -> new ArrayList<>()).add(d);
        }

        // BFS — visited maps processId → depth (lowest depth reached).
        Map<Long, Integer> depthByProcess = new LinkedHashMap<>();
        depthByProcess.put(failedProcessId, 0);
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(failedProcessId);
        while (!queue.isEmpty()) {
            Long upstream = queue.poll();
            int curDepth = depthByProcess.get(upstream);
            if (curDepth >= MAX_DEPTH) continue;
            for (ProcessDependency d : consumersOf.getOrDefault(upstream, List.of())) {
                Long consumer = d.getProcessId();
                if (depthByProcess.containsKey(consumer)) continue;     // already visited
                if (Objects.equals(consumer, failedProcessId)) continue; // self-loop guard
                depthByProcess.put(consumer, curDepth + 1);
                queue.add(consumer);
            }
        }

        // Build per-process rows (skip the failed process itself in the impact list)
        List<ImpactRow> impactRows = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : depthByProcess.entrySet()) {
            Long pid = e.getKey();
            int depth = e.getValue();
            if (depth == 0) continue;
            CriticalProcess p = processById.get(pid);
            if (p == null) continue;

            int reachHour = depthToHour(depth);
            BiaRecord b = biaByProcess.get(pid);
            Integer mtpd = b == null ? null : b.getMtpdHours();
            Integer rto  = b == null ? null : b.getRtoHours();
            Integer threshold = mtpd != null ? mtpd : rto;

            boolean breached = threshold != null && reachHour >= threshold;
            double confidence = depthToConfidence(depth);

            impactRows.add(new ImpactRow(
                pid,
                p.getProcessNameEn(),
                p.getProcessNameAr(),
                p.getCriticality(),
                depth,
                reachHour,
                mtpd, rto,
                breached,
                round2(confidence)
            ));
        }
        // Sort: breached first, then by depth asc, then by criticality desc-ish.
        impactRows.sort(Comparator
            .comparing((ImpactRow r) -> !r.breached())   // false (breached=true) first
            .thenComparingInt(ImpactRow::depth)
            .thenComparingInt((ImpactRow r) -> -criticalityRank(r.criticality())));

        // Build by-time-band buckets
        Map<Integer, BandSummary> byBand = new LinkedHashMap<>();
        for (int h : HOUR_BANDS) {
            long total    = impactRows.stream().filter(r -> r.reachHour() <= h).count();
            long breached = impactRows.stream().filter(r -> r.reachHour() <= h && r.breached()).count();
            byBand.put(h, new BandSummary((int) total, (int) breached));
        }

        CriticalProcess source = processById.get(failedProcessId);
        return new CascadeResult(
            failedProcessId,
            source.getProcessNameEn(),
            source.getProcessNameAr(),
            source.getCriticality(),
            impactRows.size(),
            impactRows.stream().filter(ImpactRow::breached).mapToInt(r -> 1).sum(),
            byBand,
            impactRows
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    static int depthToHour(int depth) {
        // Heuristic v1 — no per-edge latency data yet.
        if (depth <= 1) return 1;
        if (depth == 2) return 4;
        if (depth == 3) return 8;
        return 24;
    }

    static double depthToConfidence(int depth) {
        if (depth <= 1) return 1.0;
        if (depth == 2) return 0.75;
        if (depth == 3) return 0.5;
        return 0.25;
    }

    private static int criticalityRank(String s) {
        if (s == null) return 0;
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH"     -> 3;
            case "MEDIUM"   -> 2;
            case "LOW"      -> 1;
            default         -> 0;
        };
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    // ─── DTO types ───────────────────────────────────────────────────

    public record CascadeResult(
        Long sourceProcessId,
        String sourceProcessNameEn,
        String sourceProcessNameAr,
        String sourceCriticality,
        int totalImpactedCount,
        int breachedCount,
        Map<Integer, BandSummary> byHourBand,   // 1h / 4h / 8h / 24h
        List<ImpactRow> impacts
    ) {}

    public record ImpactRow(
        Long processId,
        String processNameEn,
        String processNameAr,
        String criticality,
        int depth,                              // edges from source
        int reachHour,                          // hour the cascade lands
        Integer mtpdHours,                      // nullable
        Integer rtoHours,                       // nullable
        boolean breached,                       // reachHour ≥ MTPD (or RTO)
        double confidence                       // 0..1 — drops with depth
    ) {}

    public record BandSummary(int impacted, int breached) {}
}
