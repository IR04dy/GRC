package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.finance.repository.AssuranceLineageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Roadmap closure (plan §0 item 10) — the ISAE 3410 assurance-lineage view:
 * proves, per disclosed object of a reporting year, that the data point exists,
 * traces to evidence, and (where the object has an attestation state) is
 * verified. Grades:
 * <ul>
 *   <li><b>COMPLETE</b> — data + evidence (+ verified where the concept applies)</li>
 *   <li><b>PARTIAL</b>  — data without evidence, or evidenced but not yet verified</li>
 *   <li><b>MISSING</b>  — no underlying data (an un-evidenceable disclosure)</li>
 * </ul>
 * The readiness percentage (COMPLETE / total) is the external-assurance posture
 * an ISAE 3410 practitioner will sample against.
 */
@Service
@RequiredArgsConstructor
public class AssuranceLineageService {

    private final AssuranceLineageRepository repo;

    public record LineageRow(String objectType, String code, String nameEn,
                             boolean hasData, boolean hasEvidence, Boolean verified, String grade) {}
    public record TypeSummary(String objectType, int total, int complete, int partial, int missing) {}
    public record LineageResponse(Short year, int total, int complete, int partial, int missing,
                                  BigDecimal readinessPct, List<TypeSummary> byType, List<LineageRow> rows) {}

    @Transactional(readOnly = true)
    public LineageResponse lineage(Short year) {
        Long org = TenantContext.requireOrganisationId();
        List<LineageRow> rows = new ArrayList<>();
        Map<String, int[]> byType = new TreeMap<>();   // [total, complete, partial, missing]
        int complete = 0, partial = 0, missing = 0;

        for (Object[] r : repo.lineageRows(org, year)) {
            String type = String.valueOf(r[0]);
            boolean hasData = ((Number) r[3]).intValue() == 1;
            boolean hasEvidence = ((Number) r[4]).intValue() == 1;
            Boolean verified = r[5] == null ? null : ((Number) r[5]).intValue() == 1;
            String grade = grade(hasData, hasEvidence, verified);
            rows.add(new LineageRow(type, String.valueOf(r[1]), String.valueOf(r[2]),
                hasData, hasEvidence, verified, grade));
            int[] t = byType.computeIfAbsent(type, k -> new int[4]);
            t[0]++;
            switch (grade) {
                case "COMPLETE" -> { complete++; t[1]++; }
                case "PARTIAL"  -> { partial++;  t[2]++; }
                default         -> { missing++;  t[3]++; }
            }
        }
        int total = rows.size();
        BigDecimal readiness = total == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(complete * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
        List<TypeSummary> summaries = byType.entrySet().stream()
            .map(e -> new TypeSummary(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
            .toList();
        return new LineageResponse(year, total, complete, partial, missing, readiness, summaries, rows);
    }

    static String grade(boolean hasData, boolean hasEvidence, Boolean verified) {
        if (!hasData) return "MISSING";
        if (!hasEvidence) return "PARTIAL";
        if (verified != null && !verified) return "PARTIAL";
        return "COMPLETE";
    }
}
