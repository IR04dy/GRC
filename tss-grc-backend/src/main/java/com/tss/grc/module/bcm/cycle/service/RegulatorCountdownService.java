package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.entity.CrisisRegulatoryNotification;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisRegulatoryNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Saudi regulator countdown timers — Slice 7-E (Sprint-7).
 *
 * <p>For every active {@link CrisisEvent} (status not CLOSED / CANCELLED),
 * compute the applicable regulator notification deadlines and surface a
 * traffic-light status. The deadlines themselves come from regulator
 * frameworks — no configuration; the law is the law:
 *
 * <table border="1">
 *   <caption>Saudi regulator notification SLAs encoded here</caption>
 *   <tr><th>Regulator</th><th>Window</th><th>Trigger condition</th></tr>
 *   <tr><td>SAMA Cyber Framework</td><td>4 hours</td>
 *       <td>severity ≥ 3 AND cyber_related</td></tr>
 *   <tr><td>SAMA BCF (operational)</td><td>6 hours</td>
 *       <td>severity ≥ 3 (any operational disruption)</td></tr>
 *   <tr><td>NCA (ECC / OTCC)</td><td>24 hours</td>
 *       <td>cyber_related (any severity)</td></tr>
 *   <tr><td>SDAIA PDPL Art. 20</td><td>72 hours</td>
 *       <td>personal_data_affected</td></tr>
 *   <tr><td>NCEMA</td><td>24 hours</td>
 *       <td>severity ≥ 4 (CRISIS or NATIONAL_EMERGENCY)</td></tr>
 *   <tr><td>NRC (nuclear/radiation)</td><td>72 hours</td>
 *       <td>(reserved — manual flag, currently surfaced only when an
 *           existing notification row exists)</td></tr>
 *   <tr><td>CMA (listed entities)</td><td>24 hours</td>
 *       <td>severity ≥ 4 — listed-entity disclosure</td></tr>
 *   <tr><td>MOH</td><td>24 hours</td>
 *       <td>severity ≥ 4 AND supplier_involved — health-sector outage</td></tr>
 * </table>
 *
 * <p>Status semantics:
 * <ul>
 *   <li>{@code NOTIFIED} — a {@link CrisisRegulatoryNotification} row exists
 *       for this (event, regulator); deadline no longer matters operationally
 *       but is kept in the response for the audit trail.</li>
 *   <li>{@code BREACHED} — deadline has passed without a notification row.</li>
 *   <li>{@code NEAR_DEADLINE} — ≤ 25% of the window remains (or ≤ 60 min for
 *       short windows ≤ 6 h).</li>
 *   <li>{@code ON_TRACK} — comfortable margin remaining.</li>
 *   <li>{@code NOT_REQUIRED} — trigger condition not met for this event;
 *       returned only when explicitly asked (off by default).</li>
 * </ul>
 *
 * <p>Pure additive — no schema changes. The {@link CrisisEvent} fields
 * {@code detectionTime}, {@code cyberRelated}, {@code personalDataAffected},
 * {@code supplierInvolved}, and {@code severity} are the inputs.
 */
@Service
@RequiredArgsConstructor
public class RegulatorCountdownService {

    public static final String SAMA_CYBER = "SAMA_CYBER";
    public static final String SAMA_BCF   = "SAMA_BCF";
    public static final String NCA        = "NCA";
    public static final String SDAIA_PDPL = "SDAIA_PDPL";
    public static final String NCEMA      = "NCEMA";
    public static final String NRC        = "NRC";
    public static final String CMA        = "CMA";
    public static final String MOH        = "MOH";

    private final CrisisEventRepository crises;
    private final CrisisRegulatoryNotificationRepository regNotifs;

    // ─── Public surface ──────────────────────────────────────────────

    /**
     * All countdowns for every active crisis event in the tenant.
     * Sorted: BREACHED first, then NEAR_DEADLINE, then ON_TRACK,
     * then NOTIFIED — within each band by hours-remaining ascending.
     */
    @Transactional(readOnly = true)
    public List<CountdownRow> activeCountdowns() {
        Long org = TenantContext.getCurrentTenant();
        List<CrisisEvent> active = crises.findByOrganisationIdAndStatusNotInOrderByIdDesc(
            org, List.of("CLOSED", "CANCELLED"));

        if (active.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now();
        List<CountdownRow> out = new ArrayList<>();
        for (CrisisEvent e : active) {
            List<CrisisRegulatoryNotification> notified =
                regNotifs.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(org, e.getId());
            Map<String, CrisisRegulatoryNotification> byRegulator = new HashMap<>();
            for (CrisisRegulatoryNotification n : notified) {
                byRegulator.putIfAbsent(n.getRegulator(), n);
            }

            for (DeadlineSpec spec : applicableDeadlines(e)) {
                CrisisRegulatoryNotification existing = byRegulator.get(spec.regulator);
                out.add(buildRow(e, spec, now, existing));
            }
        }
        sortByUrgency(out);
        return out;
    }

    /**
     * Countdowns for a single crisis event — used by the Crisis Room page
     * to surface every regulator the on-call team must call within the next
     * few hours.
     */
    @Transactional(readOnly = true)
    public List<CountdownRow> forCrisisEvent(Long crisisEventId) {
        Long org = TenantContext.getCurrentTenant();
        CrisisEvent e = crises.findByIdAndOrganisationId(crisisEventId, org)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Crisis event not found: " + crisisEventId));

        List<CrisisRegulatoryNotification> notified =
            regNotifs.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(org, e.getId());
        Map<String, CrisisRegulatoryNotification> byRegulator = new HashMap<>();
        for (CrisisRegulatoryNotification n : notified) {
            byRegulator.putIfAbsent(n.getRegulator(), n);
        }

        LocalDateTime now = LocalDateTime.now();
        List<CountdownRow> out = new ArrayList<>();
        for (DeadlineSpec spec : applicableDeadlines(e)) {
            out.add(buildRow(e, spec, now, byRegulator.get(spec.regulator)));
        }
        // Also include any notifications already sent for regulators that
        // didn't auto-trigger (e.g. manual NRC). Keeps the audit trail visible.
        for (CrisisRegulatoryNotification n : notified) {
            if (out.stream().noneMatch(r -> r.regulator().equals(n.getRegulator()))) {
                out.add(buildManualNotifiedRow(e, n, now));
            }
        }
        sortByUrgency(out);
        return out;
    }

    /** Tenant-wide summary — counts by status; for executive dashboards. */
    @Transactional(readOnly = true)
    public CountdownSummary summary() {
        List<CountdownRow> rows = activeCountdowns();
        long breached      = rows.stream().filter(r -> "BREACHED".equals(r.status())).count();
        long nearDeadline  = rows.stream().filter(r -> "NEAR_DEADLINE".equals(r.status())).count();
        long onTrack       = rows.stream().filter(r -> "ON_TRACK".equals(r.status())).count();
        long notified      = rows.stream().filter(r -> "NOTIFIED".equals(r.status())).count();
        return new CountdownSummary(rows.size(), breached, nearDeadline, onTrack, notified);
    }

    // ─── Deadline rules ──────────────────────────────────────────────

    private List<DeadlineSpec> applicableDeadlines(CrisisEvent e) {
        int severity = e.getSeverity() == null ? 1 : e.getSeverity();
        boolean cyber    = Boolean.TRUE.equals(e.getCyberRelated());
        boolean pdpl     = Boolean.TRUE.equals(e.getPersonalDataAffected());
        boolean supplier = Boolean.TRUE.equals(e.getSupplierInvolved());

        List<DeadlineSpec> out = new ArrayList<>(8);

        // SAMA — banking sector
        if (severity >= 3 && cyber) {
            out.add(new DeadlineSpec(SAMA_CYBER, 4, "SAMA Cyber Framework — material cyber incident"));
        }
        if (severity >= 3) {
            out.add(new DeadlineSpec(SAMA_BCF, 6, "SAMA BCF — material operational disruption"));
        }

        // NCA — any cyber-related incident on a national-critical entity
        if (cyber) {
            out.add(new DeadlineSpec(NCA, 24, "NCA — cybersecurity incident notification"));
        }

        // SDAIA PDPL Article 20 — 72h personal data breach
        if (pdpl) {
            out.add(new DeadlineSpec(SDAIA_PDPL, 72, "SDAIA PDPL Article 20 — personal data breach"));
        }

        // NCEMA — civil emergencies / national-impact disruptions
        if (severity >= 4) {
            out.add(new DeadlineSpec(NCEMA, 24, "NCEMA — national-impact crisis"));
        }

        // CMA — listed-entity material event disclosure
        if (severity >= 4) {
            out.add(new DeadlineSpec(CMA, 24, "CMA — listed-entity material event disclosure"));
        }

        // MOH — only fires when both severity is high and a supplier is involved
        // (e.g. critical medical device / OT supplier outage)
        if (severity >= 4 && supplier) {
            out.add(new DeadlineSpec(MOH, 24, "MOH — health-sector critical disruption"));
        }

        return out;
    }

    private CountdownRow buildRow(CrisisEvent e, DeadlineSpec spec, LocalDateTime now,
                                  CrisisRegulatoryNotification existing) {
        LocalDateTime anchor = e.getDetectionTime() != null ? e.getDetectionTime() : e.getCreatedAt();
        LocalDateTime deadline = anchor.plusHours(spec.windowHours);

        if (existing != null) {
            // Already notified — record the original deadline + the notification
            // for the audit trail, but mark as NOTIFIED so the urgency drops.
            long minsBetween = Duration.between(deadline, existing.getNotifiedAt()).toMinutes();
            return new CountdownRow(
                e.getId(), e.getEventCode(), e.getTitleEn(), e.getTitleAr(),
                e.getSeverity(), e.getStatus(),
                spec.regulator, spec.windowHours, spec.legalBasisEn,
                anchor, deadline, now,
                /* hoursRemaining = */ 0,
                /* minutesRemaining = */ 0,
                "NOTIFIED",
                existing.getId(),
                existing.getNotifiedAt(),
                existing.getReferenceNumber(),
                /* notifiedLate = */ minsBetween > 0
            );
        }

        Duration left = Duration.between(now, deadline);
        long minutesLeft = left.toMinutes();
        long hoursLeftRound = Math.round(left.toMinutes() / 60.0);

        String status;
        if (minutesLeft <= 0) {
            status = "BREACHED";
        } else {
            // ≤25% of window remaining OR ≤60 min for tight windows
            long thresholdMin = Math.max(60, (long)(spec.windowHours * 60 * 0.25));
            status = (minutesLeft <= thresholdMin) ? "NEAR_DEADLINE" : "ON_TRACK";
        }

        return new CountdownRow(
            e.getId(), e.getEventCode(), e.getTitleEn(), e.getTitleAr(),
            e.getSeverity(), e.getStatus(),
            spec.regulator, spec.windowHours, spec.legalBasisEn,
            anchor, deadline, now,
            hoursLeftRound, minutesLeft,
            status,
            null, null, null,
            false
        );
    }

    private CountdownRow buildManualNotifiedRow(CrisisEvent e, CrisisRegulatoryNotification n, LocalDateTime now) {
        return new CountdownRow(
            e.getId(), e.getEventCode(), e.getTitleEn(), e.getTitleAr(),
            e.getSeverity(), e.getStatus(),
            n.getRegulator(), 0, "Manual notification (no automated SLA computed)",
            e.getDetectionTime() != null ? e.getDetectionTime() : e.getCreatedAt(),
            null, now, 0, 0,
            "NOTIFIED",
            n.getId(), n.getNotifiedAt(), n.getReferenceNumber(),
            false
        );
    }

    private static void sortByUrgency(List<CountdownRow> rows) {
        // BREACHED first, then NEAR_DEADLINE, then ON_TRACK, then NOTIFIED.
        // Within each band: most-urgent (lowest minutesRemaining) first.
        Map<String, Integer> rank = Map.of(
            "BREACHED",      0,
            "NEAR_DEADLINE", 1,
            "ON_TRACK",      2,
            "NOTIFIED",      3,
            "NOT_REQUIRED",  4
        );
        rows.sort(Comparator
            .comparingInt((CountdownRow r) -> rank.getOrDefault(r.status(), 99))
            .thenComparingLong(CountdownRow::minutesRemaining));
    }

    // ─── DTO types ───────────────────────────────────────────────────

    /** One row per (crisis_event × regulator) — Saudi regulator countdown timer. */
    public record CountdownRow(
        Long crisisEventId,
        String eventCode,
        String titleEn,
        String titleAr,
        Integer severity,
        String eventStatus,

        String regulator,
        int windowHours,
        String legalBasisEn,

        LocalDateTime anchorAt,           // detectionTime (or createdAt fallback)
        LocalDateTime deadlineAt,         // anchor + windowHours
        LocalDateTime evaluatedAt,
        long hoursRemaining,              // rounded to nearest hour (signed)
        long minutesRemaining,            // signed — negative means past deadline

        /** ON_TRACK | NEAR_DEADLINE | BREACHED | NOTIFIED | NOT_REQUIRED */
        String status,

        // Populated when status == NOTIFIED
        Long notificationId,
        LocalDateTime notifiedAt,
        String referenceNumber,
        boolean notifiedLate
    ) {}

    public record CountdownSummary(
        long totalRows,
        long breachedCount,
        long nearDeadlineCount,
        long onTrackCount,
        long notifiedCount
    ) {}

    private record DeadlineSpec(String regulator, int windowHours, String legalBasisEn) {}
}
