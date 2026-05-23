package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.RegulatoryChange;
import com.tss.grc.module.compliance.repository.RegulatoryChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Regulatory horizon scanner — Sprint-8 Slice 8-B.
 *
 * <p>Consolidates every not-yet-closed {@link RegulatoryChange} with a
 * future or near-past {@code effectiveDate} into a single tenant-wide
 * horizon view. Bucketed:
 *
 * <ul>
 *   <li>{@code IMMINENT} — effective in ≤ 30 days (red)</li>
 *   <li>{@code NEAR_TERM} — effective in 31–90 days (amber)</li>
 *   <li>{@code QUARTER}  — effective in 91–180 days (yellow)</li>
 *   <li>{@code HORIZON}  — effective > 180 days (green)</li>
 *   <li>{@code PAST_DUE} — effective date passed but status not CLOSED (dark red)</li>
 * </ul>
 *
 * <p>Pure read aggregator — no schema changes. Reads
 * {@code compliance.regulatory_changes} which is already
 * tenant-scoped and populated by the existing change-tracking
 * workflow ({@link RegulatoryChangeService}).
 */
@Service
@RequiredArgsConstructor
public class RegulatoryHorizonService {

    private static final List<String> ACTIVE_STATUSES = List.of(
        "NEW", "APPLICABILITY_ASSESSED", "IMPACT_ASSESSED", "CONTROLS_UPDATED");

    private final RegulatoryChangeRepository changes;

    @Transactional(readOnly = true)
    public Horizon scan() {
        Long org = TenantContext.getCurrentTenant();
        LocalDate today = LocalDate.now();
        // Reach a year back so PAST_DUE rows still show up — anything older
        // is essentially a process failure and should be archived manually.
        List<RegulatoryChange> rows = changes.findHorizon(org, today.minusDays(365));

        List<HorizonRow> mapped = rows.stream()
            .filter(r -> ACTIVE_STATUSES.contains(r.getStatus()))
            .map(r -> toRow(r, today))
            .collect(Collectors.toList());

        // Bucket counts
        Map<String, Long> byBucket = new LinkedHashMap<>();
        byBucket.put("PAST_DUE",  0L);
        byBucket.put("IMMINENT",  0L);
        byBucket.put("NEAR_TERM", 0L);
        byBucket.put("QUARTER",   0L);
        byBucket.put("HORIZON",   0L);
        for (HorizonRow r : mapped) byBucket.merge(r.bucket(), 1L, Long::sum);

        // By authority
        Map<String, Long> byAuthority = new LinkedHashMap<>();
        for (HorizonRow r : mapped) byAuthority.merge(r.sourceAuthority(), 1L, Long::sum);

        // By impact level — CRITICAL / HIGH ranked first
        Map<String, Long> byImpact = new LinkedHashMap<>();
        byImpact.put("CRITICAL", 0L);
        byImpact.put("HIGH",     0L);
        byImpact.put("MEDIUM",   0L);
        byImpact.put("LOW",      0L);
        byImpact.put("UNKNOWN",  0L);
        for (HorizonRow r : mapped) byImpact.merge(r.impactLevel(), 1L, Long::sum);

        return new Horizon(today, mapped.size(), byBucket, byAuthority, byImpact, mapped);
    }

    private HorizonRow toRow(RegulatoryChange r, LocalDate today) {
        long days = r.getEffectiveDate() == null
            ? Long.MAX_VALUE
            : ChronoUnit.DAYS.between(today, r.getEffectiveDate());

        String bucket;
        if (days < 0)        bucket = "PAST_DUE";
        else if (days <= 30) bucket = "IMMINENT";
        else if (days <= 90) bucket = "NEAR_TERM";
        else if (days <= 180) bucket = "QUARTER";
        else                 bucket = "HORIZON";

        return new HorizonRow(
            r.getId(),
            r.getSourceAuthority(),
            r.getTitleEn(),
            r.getTitleAr(),
            r.getCitation(),
            r.getEffectiveDate(),
            (int) days,
            bucket,
            r.getApplicability(),
            r.getImpactLevel() == null ? "UNKNOWN" : r.getImpactLevel(),
            r.getStatus(),
            r.getAssignedToId()
        );
    }

    // ─── DTO types ───────────────────────────────────────────────────

    public record Horizon(
        LocalDate evaluatedOn,
        int activeChangeCount,
        Map<String, Long> byBucket,
        Map<String, Long> bySourceAuthority,
        Map<String, Long> byImpactLevel,
        List<HorizonRow> rows
    ) {}

    public record HorizonRow(
        Long id,
        String sourceAuthority,        // SAMA / NCA / SDAIA / CMA / NCEMA / ZATCA / MOH / OTHER
        String titleEn,
        String titleAr,
        String citation,
        LocalDate effectiveDate,
        int daysUntilEffective,        // negative for PAST_DUE
        String bucket,                 // PAST_DUE / IMMINENT / NEAR_TERM / QUARTER / HORIZON
        String applicability,          // UNKNOWN / APPLICABLE / NOT_APPLICABLE / PARTIAL
        String impactLevel,            // UNKNOWN / LOW / MEDIUM / HIGH / CRITICAL
        String status,
        Long assignedToId
    ) {}
}
