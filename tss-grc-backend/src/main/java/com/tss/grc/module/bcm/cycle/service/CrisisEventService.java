package com.tss.grc.module.bcm.cycle.service;

import java.util.Map;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * Crisis Event service — independent audit Tier-1 fix.
 *
 * <h3>State machine</h3>
 * <p>⚠ F2-305 — this documented <b>7 of the 11 edges</b> {@code TRANSITIONS} allows, and the four
 * it omitted are the ones a reader most needs: {@code DETECTED -> ACTIVATED} (a crisis obvious on
 * sight, skipping assessment), {@code ASSESSING -> CANCELLED}, {@code ACTIVATED -> RECOVERED}, and
 * {@code CONTAINED -> ACTIVATED} — the machine's only backwards edge, a <b>re-escalation</b> of a
 * crisis that was contained and then worsened. Written as a complete table that
 * {@code TransitionRefusalContractTest} parses and compares against {@code TRANSITIONS}.
 *
 * <pre>
 *   DETECTED  -> ASSESSING | ACTIVATED | CANCELLED
 *   ASSESSING -> ACTIVATED | CLOSED | CANCELLED
 *   ACTIVATED -> CONTAINED | RECOVERED
 *   CONTAINED -> RECOVERED | ACTIVATED
 *   RECOVERED -> CLOSED
 *   CLOSED    -> (terminal)
 *   CANCELLED -> (terminal)
 * </pre>
 * <ul>
 *   <li>{@code DETECTED}     — event logged, severity not yet confirmed</li>
 *   <li>{@code ASSESSING}    — crisis team triaging</li>
 *   <li>{@code ACTIVATED}    — crisis team activated, BCP/DRP being run</li>
 *   <li>{@code CONTAINED}    — disruption stopped spreading, recovery underway</li>
 *   <li>{@code RECOVERED}    — services restored, monitoring ongoing</li>
 *   <li>{@code CLOSED}       — terminal: root cause identified + lessons learned linked</li>
 *   <li>{@code CANCELLED}    — terminal: false alarm</li>
 * </ul>
 *
 * <h3>F8 events emitted</h3>
 * <ul>
 *   <li>{@code BCM_CRISIS_DETECTED}                 — every new event</li>
 *   <li>{@code BCM_CRISIS_HIGH_SEVERITY}            — severity ≥ 4 (CRISIS / NATIONAL_EMERGENCY) — for exec notification (spec §7.16)</li>
 *   <li>{@code BCM_CRISIS_PDPL_BREACH}              — when {@code personalDataAffected = true} (spec §7.15)</li>
 *   <li>{@code BCM_CRISIS_CYBER_LINKED}             — when {@code cyberRelated = true}</li>
 *   <li>{@code BCM_CRISIS_SUPPLIER_LINKED}          — when {@code supplierInvolved = true}</li>
 *   <li>{@code BCM_CRISIS_BCP_ACTIVATED}            — every BCP activation</li>
 *   <li>{@code BCM_CRISIS_DRP_ACTIVATED}            — every DRP activation</li>
 *   <li>{@code BCM_CRISIS_CLOSED}                   — closure (closure requires root cause + lessons per spec §J.13)</li>
 * </ul>
 *
 * <h3>Closure rule</h3>
 * <p>{@link #transition} blocks the move to CLOSED unless {@code rootCause} is
 * populated AND {@code lessonsLearnedId} is set. This enforces spec §J rule
 * "Closure requires root cause + lessons learned".
 */
@Service
@RequiredArgsConstructor
public class CrisisEventService {

    private static final Set<String> STATUSES = Set.of(
        "DETECTED", "ASSESSING", "ACTIVATED", "CONTAINED", "RECOVERED", "CLOSED", "CANCELLED");
    /** F2-220 — the state a crisis event is CREATED in. transition() owns the rest and,
     *  per spec §J.13, refuses CLOSED without a root cause and a lessons-learned record. */
    private static final String INITIAL_STATUS = "DETECTED";
    private static final Set<String> TRIGGER_SOURCES = Set.of(
        "MANUAL", "INCIDENT", "EXERCISE", "THREAT_INTEL", "SUPPLIER", "CYBER", "EXTERNAL_ALERT");
    private static final Set<String> REGULATORS = Set.of(
        "NCEMA", "SAMA", "NCA", "CMA", "PDPL", "CIVIL_DEFENCE", "MOH", "MOE", "OTHER");
    private static final Set<String> NOTIFICATION_METHODS = Set.of(
        "EMAIL", "PORTAL", "PHONE", "LETTER", "API");
    private static final Set<String> SITREP_STATUSES = Set.of(
        "WORSENING", "STABILISING", "RECOVERING", "NORMAL");

    private final CrisisEventRepository events;
    private final CrisisSituationReportRepository sitreps;
    private final CrisisDecisionRepository decisions;
    private final CrisisRegulatoryNotificationRepository regulatoryNotifications;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // Slice 9-F regulatory windows. Kept in lock-step with
    // RegulatorCountdownService — duplicated intentionally because the
    // mobile summary only needs the headline "shortest deadline" and
    // pulling the full countdown service onto the mobile path would be
    // overkill for a 6-card list.
    private static final int PDPL_WINDOW_HOURS = 72;
    private static final int SAMA_CYBER_WINDOW_HOURS = 4;
    private static final int SAMA_BCF_WINDOW_HOURS = 6;
    private static final int NCA_WINDOW_HOURS = 24;

    // ─── Event CRUD ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CrisisEvent> list(int page, int size) {
        return events.findByOrganisationIdOrderByIdDesc(
            TenantContext.getCurrentTenant(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<CrisisEvent> listActive() {
        // Active = anything that's not terminal
        return events.findByOrganisationIdAndStatusNotInOrderByIdDesc(
            TenantContext.getCurrentTenant(), List.of("CLOSED", "CANCELLED"));
    }

    /**
     * Slice 9-F — compact summary tailored for the mobile Crisis Room.
     *
     * <p>Each row carries only the fields the thumb-sized card needs:
     * id / code / severity / status / openedAt / ownerUserId plus the
     * <b>shortest applicable regulatory deadline</b> across the SAMA-cyber
     * (4h), SAMA-BCF (6h), NCA (24h), PDPL Art.23 (72h), and NCEMA (24h)
     * windows. Sort order:
     * <ol>
     *   <li>highest severity first</li>
     *   <li>then earliest regulatory deadline (already-breached events sort first)</li>
     *   <li>then by detection time descending</li>
     * </ol>
     *
     * <p>Comparing this to the heavier {@link RegulatorCountdownService}:
     * that one explodes one event into N regulator rows for the desktop
     * Crisis Room. The mobile summary deliberately collapses to one row per
     * event, picking only the most-urgent deadline.
     */
    @Transactional(readOnly = true)
    public List<ActiveCrisisSummaryRow> getActiveCrisisSummary() {
        Long org = TenantContext.requireOrganisationId();
        List<CrisisEvent> active = events.findByOrganisationIdAndStatusNotInOrderByIdDesc(
            org, List.of("CLOSED", "CANCELLED"));
        if (active.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now();
        List<ActiveCrisisSummaryRow> rows = new ArrayList<>();
        for (CrisisEvent e : active) {
            rows.add(toSummaryRow(e, now));
        }
        rows.sort(ACTIVE_SUMMARY_ORDER);
        return rows;
    }

    /** Maps one crisis event onto its mobile-summary row. */
    private static ActiveCrisisSummaryRow toSummaryRow(CrisisEvent e, LocalDateTime now) {
        LocalDateTime anchor = e.getDetectionTime() != null
            ? e.getDetectionTime()
            : (e.getStartTime() != null ? e.getStartTime() : e.getCreatedAt());

        Deadline deadline = RegulatoryDeadlineCalculator.shortestDeadline(e, anchor);
        LocalDateTime shortestDeadline = deadline == null ? null : deadline.at();
        String shortestRegulator = deadline == null ? null : deadline.regulator();

        Long minutesRemaining = null;
        if (shortestDeadline != null) {
            minutesRemaining = java.time.Duration.between(now, shortestDeadline).toMinutes();
        }

        int sev = e.getSeverity() == null ? 1 : e.getSeverity();

        return new ActiveCrisisSummaryRow(
            e.getId(),
            e.getEventCode(),
            e.getTitleEn(),
            e.getTitleAr(),
            sev,
            e.getStatus(),
            e.getCrisisManagerId(),
            escalationTier(sev),
            anchor,
            shortestRegulator,
            shortestDeadline,
            minutesRemaining,
            Boolean.TRUE.equals(e.getPersonalDataAffected()),
            Boolean.TRUE.equals(e.getCyberRelated()),
            Boolean.TRUE.equals(e.getSupplierInvolved()),
            nextActionLabel(e.getStatus())
        );
    }

    /**
     * Escalation tier — convenient single-letter for the mobile chip:
     *   T1 (sev 4-5) — critical / national
     *   T2 (sev 3)   — major disruption
     *   T3 (sev 1-2) — operational
     */
    private static String escalationTier(int sev) {
        if (sev >= 4)      return "T1";
        else if (sev == 3) return "T2";
        else               return "T3";
    }

    /** nextActionLabel reflects the workflow stage in Saudi-native shorthand. */
    private static String nextActionLabel(String status) {
        return switch (Objects.requireNonNullElse(status, "DETECTED")) {
            case "DETECTED"  -> "بحاجة للتقييم";          // needs triage
            case "ASSESSING" -> "بحاجة للتفعيل";          // needs activation
            case "ACTIVATED" -> "بحاجة للاحتواء";         // needs containment
            case "CONTAINED" -> "بحاجة للاسترداد";        // needs recovery
            case "RECOVERED" -> "جاهز للإغلاق";           // ready to close
            default          -> "متابعة";                  // follow-up
        };
    }

    /** Sort: severity desc, then earliest deadline first (null last), then anchor desc. */
    private static final Comparator<ActiveCrisisSummaryRow> ACTIVE_SUMMARY_ORDER = (a, b) -> {
        int s = Integer.compare(b.severity(), a.severity());
        if (s != 0) return s;
        if (a.regulatoryDeadlineAt() == null && b.regulatoryDeadlineAt() == null) {
            // both null → fall through
        } else if (a.regulatoryDeadlineAt() == null) return 1;
        else if (b.regulatoryDeadlineAt() == null) return -1;
        else {
            int d = a.regulatoryDeadlineAt().compareTo(b.regulatoryDeadlineAt());
            if (d != 0) return d;
        }
        if (a.openedAt() == null && b.openedAt() == null) return 0;
        if (a.openedAt() == null) return 1;
        if (b.openedAt() == null) return -1;
        return b.openedAt().compareTo(a.openedAt());
    };

    /** A single regulator deadline ({@code at}) the mobile summary may surface as the headline. */
    public record Deadline(String regulator, LocalDateTime at) {}

    /**
     * Computes the shortest applicable regulatory deadline for a crisis event.
     * Kept as a small static helper so the multi-regulator window logic lives in
     * one place; identical evaluation order to the previous inline version:
     * PDPL (72h) seeds first, then NCA (24h) / SAMA-cyber (4h) for cyber events,
     * then SAMA-BCF (6h, sev ≥ 3), then NCEMA (24h, sev ≥ 4) — each only replacing
     * the running shortest when strictly earlier.
     */
    static final class RegulatoryDeadlineCalculator {
        private RegulatoryDeadlineCalculator() {}

        static Deadline shortestDeadline(CrisisEvent e, LocalDateTime anchor) {
            LocalDateTime shortestDeadline = null;
            String shortestRegulator = null;

            if (Boolean.TRUE.equals(e.getPersonalDataAffected()) && anchor != null) {
                shortestDeadline = anchor.plusHours(PDPL_WINDOW_HOURS);
                shortestRegulator = "SDAIA_PDPL";
            }
            if (Boolean.TRUE.equals(e.getCyberRelated()) && anchor != null) {
                LocalDateTime d = anchor.plusHours(NCA_WINDOW_HOURS);
                if (shortestDeadline == null || d.isBefore(shortestDeadline)) {
                    shortestDeadline = d;
                    shortestRegulator = "NCA";
                }
                if (e.getSeverity() != null && e.getSeverity() >= 3) {
                    LocalDateTime samaC = anchor.plusHours(SAMA_CYBER_WINDOW_HOURS);
                    if (samaC.isBefore(shortestDeadline)) {
                        shortestDeadline = samaC;
                        shortestRegulator = "SAMA_CYBER";
                    }
                }
            }
            if (e.getSeverity() != null && e.getSeverity() >= 3 && anchor != null) {
                LocalDateTime samaB = anchor.plusHours(SAMA_BCF_WINDOW_HOURS);
                if (shortestDeadline == null || samaB.isBefore(shortestDeadline)) {
                    shortestDeadline = samaB;
                    shortestRegulator = "SAMA_BCF";
                }
            }
            if (e.getSeverity() != null && e.getSeverity() >= 4 && anchor != null) {
                LocalDateTime ncema = anchor.plusHours(NCA_WINDOW_HOURS);
                if (shortestDeadline == null || ncema.isBefore(shortestDeadline)) {
                    shortestDeadline = ncema;
                    shortestRegulator = "NCEMA";
                }
            }

            return shortestDeadline == null ? null : new Deadline(shortestRegulator, shortestDeadline);
        }
    }

    /**
     * Compact row tailored for the mobile Crisis Room card list.
     * Distinct from {@link RegulatorCountdownService.CountdownRow} which
     * is one row per (event × regulator) — this one is one row per event.
     */
    public record ActiveCrisisSummaryRow(
        Long id,
        String code,
        String titleEn,
        String titleAr,
        Integer severity,
        String status,
        Long ownerUserId,
        String escalationTier,
        LocalDateTime openedAt,
        String regulatoryRegulator,
        LocalDateTime regulatoryDeadlineAt,
        Long regulatoryMinutesRemaining,
        Boolean personalDataAffected,
        Boolean cyberRelated,
        Boolean supplierInvolved,
        String nextActionLabel
    ) {}

    @Transactional(readOnly = true)
    public CrisisEvent get(Long id) {
        return events.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("Crisis event not found: " + id));
    }

    @Transactional
    public CrisisEvent create(CrisisEvent body) {
        validateBase(body);
        body.setId(null);
        Long org = TenantContext.getCurrentTenant();
        body.setOrganisationId(org);
        // F2-220 — a DEFAULT with no vocabulary check, and bcm.crisis_events has no CHECK on
        // status either, so a crisis could be created straight to CLOSED. transition() enforces
        // spec §J.13 there: a root cause and a lessons-learned record before closure. Probed
        // live 2026-08-16 as rmanager — created CLOSED with neither, no closure_time, and none
        // of the BCM_CRISIS_* events a crisis is supposed to leave behind.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_STATUS, "a crisis event",
            "POST /api/v1/bcm/crisis-events/{id}/transition"));
        body.setActivationTime(null);
        body.setClosureTime(null);
        if (body.getEventCode() == null || body.getEventCode().isBlank()) {
            body.setEventCode(nextCode());
        }
        if (body.getDetectionTime() == null) body.setDetectionTime(LocalDateTime.now());
        if (body.getStartTime() == null)     body.setStartTime(body.getDetectionTime());
        // F2-238 — these four were field initialisers on CrisisEvent, which made update()'s
        // null-guards decorative: a PATCH naming only crisisManagerId reset the crisis to
        // severity 1 and cleared all three impact flags. They are NOT NULL columns, so the
        // default has to live on the create path; see the class-level note on update().
        if (body.getSeverity() == null)             body.setSeverity(1);
        if (body.getPersonalDataAffected() == null) body.setPersonalDataAffected(false);
        if (body.getCyberRelated() == null)         body.setCyberRelated(false);
        if (body.getSupplierInvolved() == null)     body.setSupplierInvolved(false);

        CrisisEvent saved = events.save(body);
        audit.log("CREATE", "CrisisEvent", saved.getId(),
            "Crisis event detected: " + saved.getEventCode() + " — sev=" + saved.getSeverity());

        // Always emit the DETECTED event so dashboards and downstream rules can react.
        publish("BCM_CRISIS_DETECTED", saved, Map.of(
            "severity",          saved.getSeverity(),
            "trigger_source",    nullSafe(saved.getTriggerSource()),
            "event_code",        saved.getEventCode()
        ));

        fanOutTriggerEvents(saved);
        return saved;
    }

    @Transactional
    public CrisisEvent update(Long id, CrisisEvent patch) {
        CrisisEvent existing = get(id);
        if ("CLOSED".equals(existing.getStatus()) || "CANCELLED".equals(existing.getStatus()))
            throw new IllegalArgumentException("Cannot edit a " + existing.getStatus() + " crisis event");
        // F2-297 — one of four hand-rolled copies of the rule F2-296 shared; see BcpPlanService
        // #update for why the copy was wrong in three ways. F2-238 removed this entity's status
        // initialiser, so an omitted status genuinely arrives as null here and a partial PATCH is
        // no longer refused; what the shared primitive adds is case-insensitive echo tolerance and
        // a message naming the route instead of a Java method.
        ServerOwnedState.requireUnchanged(
            patch.getStatus(), existing.getStatus(), "status", "Crisis event " + id,
            "POST /api/v1/bcm/crisis-events/" + id + "/transition, which walks the NCEMA"
            + " lifecycle and records who moved it",
            null);

        if (patch.getTitleEn()              != null) existing.setTitleEn(patch.getTitleEn());
        if (patch.getTitleAr()              != null) existing.setTitleAr(patch.getTitleAr());
        if (patch.getDescription()          != null) existing.setDescription(patch.getDescription());
        if (patch.getSeverity()             != null) {
            validateSeverity(patch.getSeverity());
            existing.setSeverity(patch.getSeverity());
        }
        if (patch.getTriggerSource()        != null) {
            validateTrigger(patch.getTriggerSource());
            existing.setTriggerSource(patch.getTriggerSource());
        }
        if (patch.getAffectedServicesJson()  != null) existing.setAffectedServicesJson(patch.getAffectedServicesJson());
        if (patch.getAffectedProcessesJson() != null) existing.setAffectedProcessesJson(patch.getAffectedProcessesJson());
        if (patch.getAffectedSitesJson()     != null) existing.setAffectedSitesJson(patch.getAffectedSitesJson());
        if (patch.getAffectedSuppliersJson() != null) existing.setAffectedSuppliersJson(patch.getAffectedSuppliersJson());
        if (patch.getAffectedSystemsJson()   != null) existing.setAffectedSystemsJson(patch.getAffectedSystemsJson());
        if (patch.getCrisisManagerId()       != null) existing.setCrisisManagerId(patch.getCrisisManagerId());
        if (patch.getCrisisTeamJson()        != null) existing.setCrisisTeamJson(patch.getCrisisTeamJson());
        if (patch.getRootCause()             != null) existing.setRootCause(patch.getRootCause());
        if (patch.getPersonalDataAffected()  != null) existing.setPersonalDataAffected(patch.getPersonalDataAffected());
        if (patch.getCyberRelated()          != null) existing.setCyberRelated(patch.getCyberRelated());
        if (patch.getSupplierInvolved()      != null) existing.setSupplierInvolved(patch.getSupplierInvolved());
        if (patch.getLessonsLearnedId()      != null) existing.setLessonsLearnedId(patch.getLessonsLearnedId());

        existing.setUpdatedAt(LocalDateTime.now());
        CrisisEvent saved = events.save(existing);
        audit.log("UPDATE", "CrisisEvent", saved.getId(),
            "Crisis event updated: " + saved.getEventCode());
        return saved;
    }

    /**
     * State transition with closure rule enforcement. Stamps activation /
     * closure timestamps automatically.
     */
    /**
     * The register's declared edges, in ONE place. F2-303 — this was an inline
     * {@code switch} of {@code Set.of(...).contains(...)}, so the legal-target list was not
     * derivable at runtime and the refusal below could only say which move was rejected, never
     * which moves would be accepted. Terminal states are present with an <b>empty</b> set rather
     * than omitted, so the terminal set is derivable from the machine instead of typed out a
     * second time ([[F2-196]]).
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DETECTED",  Set.of("ASSESSING", "ACTIVATED", "CANCELLED"),
        "ASSESSING", Set.of("ACTIVATED", "CLOSED", "CANCELLED"),
        "ACTIVATED", Set.of("CONTAINED", "RECOVERED"),
        // CONTAINED → ACTIVATED is deliberate: re-escalation of a crisis that was contained and
        // then worsened. It is the machine's only backwards edge and it must survive this
        // conversion, which is why a test names it.
        "CONTAINED", Set.of("RECOVERED", "ACTIVATED"),
        "RECOVERED", Set.of("CLOSED"),
        "CLOSED",    Set.of(),
        "CANCELLED", Set.of());

    @Transactional
    public CrisisEvent transition(Long id, String toStatus) {
        CrisisEvent e = get(id);
        if (toStatus == null || !STATUSES.contains(toStatus))
            throw new IllegalArgumentException("status must be one of " + STATUSES);

        String from = e.getStatus();
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(toStatus))
            throw new IllegalTransitionException(from, toStatus, allowed);

        // Closure rule per spec §J.13: root cause + lessons learned required.
        if ("CLOSED".equals(toStatus)) {
            if (e.getRootCause() == null || e.getRootCause().isBlank())
                throw new IllegalArgumentException("rootCause is required before closing a crisis event");
            if (e.getLessonsLearnedId() == null)
                throw new IllegalArgumentException("lessonsLearnedId must be set before closing a crisis event");
        }

        e.setStatus(toStatus);
        LocalDateTime now = LocalDateTime.now();
        if ("ACTIVATED".equals(toStatus) && e.getActivationTime() == null) {
            e.setActivationTime(now);
        }
        if ("CLOSED".equals(toStatus) || "CANCELLED".equals(toStatus)) {
            e.setClosureTime(now);
        }
        e.setUpdatedAt(now);
        CrisisEvent saved = events.save(e);
        audit.log("TRANSITION", "CrisisEvent", saved.getId(),
            "Crisis event " + saved.getEventCode() + " " + from + " → " + toStatus);

        if ("CLOSED".equals(toStatus)) {
            publish("BCM_CRISIS_CLOSED", saved, Map.of(
                "severity",  saved.getSeverity(),
                "event_code", saved.getEventCode()
            ));
        }
        return saved;
    }

    // ─── Activation actions ──────────────────────────────────────

    @Transactional
    public CrisisEvent activateBcp(Long eventId, Long bcpId) {
        CrisisEvent e = get(eventId);
        e.setRelatedBcpIdsJson(appendToJsonArray(e.getRelatedBcpIdsJson(), bcpId));
        e.setUpdatedAt(LocalDateTime.now());
        CrisisEvent saved = events.save(e);
        audit.log("CUSTOM", "CrisisEvent", saved.getId(),
            "BCP activated: bcpId=" + bcpId + " on crisis " + saved.getEventCode());
        publish("BCM_CRISIS_BCP_ACTIVATED", saved, Map.of(
            "bcp_id",     bcpId,
            "event_code", saved.getEventCode(),
            "severity",   saved.getSeverity()
        ));
        return saved;
    }

    @Transactional
    public CrisisEvent activateDrp(Long eventId, Long drpId) {
        CrisisEvent e = get(eventId);
        e.setRelatedDrpIdsJson(appendToJsonArray(e.getRelatedDrpIdsJson(), drpId));
        e.setUpdatedAt(LocalDateTime.now());
        CrisisEvent saved = events.save(e);
        audit.log("CUSTOM", "CrisisEvent", saved.getId(),
            "DRP activated: drpId=" + drpId + " on crisis " + saved.getEventCode());
        publish("BCM_CRISIS_DRP_ACTIVATED", saved, Map.of(
            "drp_id",     drpId,
            "event_code", saved.getEventCode(),
            "severity",   saved.getSeverity()
        ));
        return saved;
    }

    // ─── Situation reports ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisSituationReport> listSitReps(Long eventId) {
        get(eventId);  // tenant guard
        return sitreps.findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(
            TenantContext.getCurrentTenant(), eventId);
    }

    @Transactional
    public CrisisSituationReport addSitRep(Long eventId, CrisisSituationReport body) {
        CrisisEvent event = get(eventId);
        if (body.getSummary() == null || body.getSummary().isBlank())
            throw new IllegalArgumentException("summary is required");
        if (body.getStatusUpdate() != null && !SITREP_STATUSES.contains(body.getStatusUpdate()))
            throw new IllegalArgumentException("statusUpdate must be one of " + SITREP_STATUSES);
        if (body.getSeverityChange() != null) validateSeverity(body.getSeverityChange());

        body.setId(null);
        body.setOrganisationId(event.getOrganisationId());
        body.setCrisisEventId(eventId);
        if (body.getReportedAt() == null) body.setReportedAt(LocalDateTime.now());

        CrisisSituationReport saved = sitreps.save(body);

        // If the SitRep escalates / de-escalates severity, propagate it onto the event.
        if (body.getSeverityChange() != null && !body.getSeverityChange().equals(event.getSeverity())) {
            event.setSeverity(body.getSeverityChange());
            event.setUpdatedAt(LocalDateTime.now());
            events.save(event);
            audit.log("UPDATE", "CrisisEvent", event.getId(),
                "Severity changed via SitRep: " + event.getEventCode() + " → sev=" + body.getSeverityChange());
            if (body.getSeverityChange() >= 4) {
                publish("BCM_CRISIS_HIGH_SEVERITY", event, Map.of(
                    "severity", body.getSeverityChange(),
                    "event_code", event.getEventCode(),
                    "via", "sitrep"
                ));
            }
        }
        audit.log("CREATE", "CrisisSituationReport", saved.getId(),
            "SitRep logged on " + event.getEventCode());
        return saved;
    }

    // ─── Decisions ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisDecision> listDecisions(Long eventId) {
        get(eventId);
        return decisions.findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(
            TenantContext.getCurrentTenant(), eventId);
    }

    @Transactional
    public CrisisDecision addDecision(Long eventId, CrisisDecision body) {
        CrisisEvent event = get(eventId);
        if (body.getDecisionText() == null || body.getDecisionText().isBlank())
            throw new IllegalArgumentException("decisionText is required");

        body.setId(null);
        body.setOrganisationId(event.getOrganisationId());
        body.setCrisisEventId(eventId);
        if (body.getDecidedAt() == null) body.setDecidedAt(LocalDateTime.now());

        CrisisDecision saved = decisions.save(body);
        audit.log("CREATE", "CrisisDecision", saved.getId(),
            "Decision logged on " + event.getEventCode() + ": " + truncate(saved.getDecisionText(), 80));
        return saved;
    }

    // ─── Regulatory notifications ────────────────────────────────

    @Transactional(readOnly = true)
    public List<CrisisRegulatoryNotification> listRegulatoryNotifications(Long eventId) {
        get(eventId);
        return regulatoryNotifications.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(
            TenantContext.getCurrentTenant(), eventId);
    }

    @Transactional
    public CrisisRegulatoryNotification logRegulatoryNotification(Long eventId,
                                                                  CrisisRegulatoryNotification body) {
        CrisisEvent event = get(eventId);
        if (body.getRegulator() == null || !REGULATORS.contains(body.getRegulator()))
            throw new IllegalArgumentException("regulator must be one of " + REGULATORS);
        if (body.getNotificationMethod() != null && !NOTIFICATION_METHODS.contains(body.getNotificationMethod()))
            throw new IllegalArgumentException("notificationMethod must be one of " + NOTIFICATION_METHODS);

        body.setId(null);
        body.setOrganisationId(event.getOrganisationId());
        body.setCrisisEventId(eventId);
        if (body.getNotifiedAt() == null) body.setNotifiedAt(LocalDateTime.now());

        CrisisRegulatoryNotification saved = regulatoryNotifications.save(body);
        audit.log("CUSTOM", "CrisisRegulatoryNotification", saved.getId(),
            "Regulator notified: " + saved.getRegulator() + " on " + event.getEventCode());
        return saved;
    }

    @Transactional
    public CrisisRegulatoryNotification recordAcknowledgement(Long eventId, Long notificationId,
                                                              String reference) {
        get(eventId);
        CrisisRegulatoryNotification n = regulatoryNotifications.findByIdAndOrganisationId(
                notificationId, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));
        if (!n.getCrisisEventId().equals(eventId))
            throw new IllegalArgumentException("Notification does not belong to this event");
        n.setAcknowledgementReceived(true);
        n.setAcknowledgementAt(LocalDateTime.now());
        n.setAcknowledgementReference(reference);
        CrisisRegulatoryNotification saved = regulatoryNotifications.save(n);
        audit.log("CUSTOM", "CrisisRegulatoryNotification", saved.getId(),
            "Ack received from " + saved.getRegulator() + " ref=" + (reference == null ? "(blank)" : reference));
        return saved;
    }

    // ─── Stats ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Long> out = new HashMap<>();
        for (String s : STATUSES) out.put(s.toLowerCase(), events.countByOrganisationIdAndStatus(org, s));
        out.put("active",        out.getOrDefault("detected", 0L)
                               + out.getOrDefault("assessing", 0L)
                               + out.getOrDefault("activated", 0L)
                               + out.getOrDefault("contained", 0L)
                               + out.getOrDefault("recovered", 0L));
        out.put("high_severity", events.countByOrganisationIdAndSeverityGreaterThanEqual(org, 4));
        return out;
    }

    // ─── Helpers ────────────────────────────────────────────────

    private void validateBase(CrisisEvent e) {
        if (e.getTitleEn() == null || e.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (e.getSeverity() == null)
            throw new IllegalArgumentException("severity is required (1..5)");
        validateSeverity(e.getSeverity());
        if (e.getTriggerSource() != null) validateTrigger(e.getTriggerSource());
    }

    private static void validateSeverity(Integer sev) {
        if (sev == null || sev < 1 || sev > 5)
            throw new IllegalArgumentException("severity must be 1..5");
    }

    private static void validateTrigger(String t) {
        if (!TRIGGER_SOURCES.contains(t))
            throw new IllegalArgumentException("triggerSource must be one of " + TRIGGER_SOURCES);
    }

    /**
     * Fan out the cross-module trigger events (PDPL / cyber / supplier / high severity).
     * Called once at create time. Each downstream module (PDPL, cyber, SCM) subscribes
     * to its own event and creates the breach / incident / supplier record.
     */
    private void fanOutTriggerEvents(CrisisEvent e) {
        if (e.getSeverity() != null && e.getSeverity() >= 4) {
            publish("BCM_CRISIS_HIGH_SEVERITY", e, Map.of(
                "severity", e.getSeverity(),
                "event_code", e.getEventCode()
            ));
        }
        if (Boolean.TRUE.equals(e.getPersonalDataAffected())) {
            publish("BCM_CRISIS_PDPL_BREACH", e, Map.of(
                "event_code", e.getEventCode(),
                "severity",   e.getSeverity()
            ));
        }
        if (Boolean.TRUE.equals(e.getCyberRelated())) {
            publish("BCM_CRISIS_CYBER_LINKED", e, Map.of(
                "event_code", e.getEventCode(),
                "severity",   e.getSeverity()
            ));
        }
        if (Boolean.TRUE.equals(e.getSupplierInvolved())) {
            publish("BCM_CRISIS_SUPPLIER_LINKED", e, Map.of(
                "event_code", e.getEventCode(),
                "severity",   e.getSeverity()
            ));
        }
    }

    private void publish(String triggerEvent, CrisisEvent e, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>(extra);
        eventPublisher.publishEvent(DomainEvent.of(
            e.getOrganisationId(), triggerEvent, "CrisisEvent", e.getId(), payload));
    }

    /** Generates an event code in the form CRIS-YYYY-NNNNNN. */
    private String nextCode() {
        Long org = TenantContext.getCurrentTenant();
        int year = Year.now().getValue();
        long count = events.findByOrganisationIdOrderByIdDesc(org, PageRequest.of(0, 1)).getTotalElements();
        return String.format("CRIS-%d-%06d", year, count + 1);
    }

    /**
     * Append a Long to a JSON array string. Empty / null input becomes a fresh array.
     * Trivial concatenation — we don't parse / re-serialise to keep this fast and
     * dependency-free; callers can always re-parse the column on read.
     */
    private static String appendToJsonArray(String existingJson, Long value) {
        if (value == null) return existingJson;
        if (existingJson == null || existingJson.isBlank() || "[]".equals(existingJson.trim())) {
            return "[" + value + "]";
        }
        // Insert before the trailing ']'
        String trimmed = existingJson.trim();
        if (!trimmed.endsWith("]")) {
            // Malformed input — start fresh
            return "[" + value + "]";
        }
        return trimmed.substring(0, trimmed.length() - 1) + "," + value + "]";
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
