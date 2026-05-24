package com.tss.grc.module.bcm.cycle.runtime.observer.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.InjectView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunResponse;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignObserverRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.AssignmentView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.FlagRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationRequest;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationView;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObservationsByType;
import com.tss.grc.module.bcm.cycle.runtime.observer.dto.ObserverDtos.ObserverDashboard;
import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObservation;
import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObserverAssignment;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObservationRepository;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObserverAssignmentRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Slice 10-C — Observer Dashboard service.
 *
 * <p>Read-only mirror of the facilitator console for auditors, internal audit,
 * CISO, and regulator observers, plus structured observation capture.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Assign / remove observers on a run (FACILITATOR / BCM_MANAGER /
 *       PLATFORM_ADMIN at the controller layer).</li>
 *   <li>Serve the full {@code ObserverDashboard} payload — run header, all
 *       injects (chronological), all responses, all observations, observer
 *       roster — in a single round-trip.</li>
 *   <li>Record observations — gated on an active assignment for the requester
 *       on the run, with the observer's choice of language.</li>
 *   <li>Flag / unflag observations for inclusion in the After-Action Report.</li>
 *   <li>Group observations by type for the end-of-run scoring screen.</li>
 *   <li>F7 audit + F8 events on every mutation:
 *       {@code EXERCISE_OBSERVER_ASSIGNED},
 *       {@code EXERCISE_OBSERVATION_RECORDED},
 *       {@code EXERCISE_OBSERVATION_FLAGGED}.</li>
 *   <li>WebSocket broadcast on observation record/flag — reuses the 10-B
 *       topic {@code /topic/org.{orgId}.exercise-run.{runId}} with new
 *       payload {@code OBSERVATION_ADDED} / {@code OBSERVATION_FLAGGED}.</li>
 * </ul>
 *
 * <h3>READ-ONLY enforcement</h3>
 * <p>The dashboard exposes <em>no</em> dispatch or transition endpoints —
 * those remain on {@code ExerciseRuntimeController}, which requires
 * {@code FACILITATOR / BCM_MANAGER / PLATFORM_ADMIN}. Observers cannot
 * accidentally dispatch an inject or change run state because there is no
 * affordance for it in this controller surface.
 */
@Service
@RequiredArgsConstructor
public class ExerciseObserverService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseObserverService.class);

    static final Set<String> OBSERVER_ROLES = Set.of(
        "INTERNAL_AUDIT", "EXTERNAL_AUDIT", "COMPLIANCE_OBSERVER",
        "SAMA_LIAISON", "NCA_LIAISON", "BOARD_OBSERVER");

    static final Set<String> OBSERVATION_TYPES = Set.of(
        "STRENGTH", "GAP", "RISK", "COMMUNICATION", "DECISION_QUALITY",
        "ESCALATION", "RECOVERY_TIME", "COMPLIANCE", "GENERAL");

    static final Set<String> SEVERITIES = Set.of(
        "INFO", "MINOR", "MODERATE", "MAJOR", "CRITICAL");

    private final ExerciseObserverAssignmentRepository assignmentRepo;
    private final ExerciseObservationRepository observationRepo;
    private final ExerciseRunRepository runRepo;
    private final ExerciseRunInjectRepository runInjectRepo;
    private final ExerciseRunResponseRepository responseRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final RealtimeBroadcaster realtime;

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    /** Pulls the current user id from the security context — null if anonymous. */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof GrcUserDetails gud) return gud.getUserId();
        return null;
    }

    // ─── Observer assignment ────────────────────────────────────────────

    /**
     * Assign a user as an observer on the run. Idempotent — if an active
     * assignment already exists for (run, observer), returns it unchanged.
     */
    @Transactional
    public AssignmentView assignObserver(Long runId, AssignObserverRequest req) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);

        String role = normaliseRole(req.observerRole());

        // Idempotent: existing active assignment short-circuits.
        Optional<ExerciseObserverAssignment> existing = assignmentRepo
            .findByOrganisationIdAndExerciseRunIdAndObserverUserId(
                orgId, runId, req.observerUserId());
        if (existing.isPresent()) {
            return toAssignmentView(existing.get());
        }

        ExerciseObserverAssignment a = new ExerciseObserverAssignment();
        a.setOrganisationId(orgId);
        a.setExerciseRunId(run.getId());
        a.setObserverUserId(req.observerUserId());
        a.setObserverRole(role);
        a.setAssignedByUserId(currentUserId());
        a.setAssignedAt(LocalDateTime.now());
        ExerciseObserverAssignment saved = assignmentRepo.save(a);

        audit.log("CREATE", "ExerciseObserverAssignment", saved.getId(),
            "Observer #" + req.observerUserId() + " (" + role
                + ") assigned to run " + run.getRunCode());

        emit(orgId, "EXERCISE_OBSERVER_ASSIGNED", "ExerciseObserverAssignment", saved.getId(), Map.of(
            "runId",          String.valueOf(run.getId()),
            "runCode",        run.getRunCode(),
            "observerUserId", String.valueOf(req.observerUserId()),
            "observerRole",   role
        ));

        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.of(
            "type",           "OBSERVER_ASSIGNED",
            "runId",          String.valueOf(run.getId()),
            "observerUserId", String.valueOf(req.observerUserId()),
            "observerRole",   role,
            "assignmentId",   String.valueOf(saved.getId())
        ));

        return toAssignmentView(saved);
    }

    /** Soft-delete the observer assignment. */
    @Transactional
    public void removeObserver(Long runId, Long assignmentId) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        ExerciseObserverAssignment a = assignmentRepo.findByIdAndOrganisationId(assignmentId, orgId)
            .filter(x -> Objects.equals(x.getExerciseRunId(), runId))
            .orElseThrow(() -> new EntityNotFoundException(
                "Observer assignment " + assignmentId + " not found on run " + runId));
        a.softDelete(currentUserId());
        assignmentRepo.save(a);

        audit.log("DELETE", "ExerciseObserverAssignment", a.getId(),
            "Observer #" + a.getObserverUserId() + " removed from run " + run.getRunCode());

        emit(orgId, "EXERCISE_OBSERVER_REMOVED", "ExerciseObserverAssignment", a.getId(), Map.of(
            "runId",          String.valueOf(run.getId()),
            "observerUserId", String.valueOf(a.getObserverUserId())
        ));

        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.of(
            "type",           "OBSERVER_REMOVED",
            "runId",          String.valueOf(run.getId()),
            "assignmentId",   String.valueOf(a.getId()),
            "observerUserId", String.valueOf(a.getObserverUserId())
        ));
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> listObservers(Long runId) {
        Long orgId = org();
        loadRun(runId, orgId);   // tenant guard
        return assignmentRepo
            .findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(orgId, runId)
            .stream().map(ExerciseObserverService::toAssignmentView).toList();
    }

    // ─── Observer dashboard (read-only mirror) ──────────────────────────

    /**
     * Build the full observer dashboard payload — single round-trip on page load.
     * Live updates afterwards come over the 10-B WebSocket topic.
     *
     * <p>Returned regardless of whether the caller is an assigned observer; the
     * controller's role-based {@code @PreAuthorize} is the auth gate for read.
     * The {@code isAssignedObserver} flag tells the UI whether to show the
     * "record observation" form.
     */
    @Transactional(readOnly = true)
    public ObserverDashboard getObserverDashboard(Long runId, Long observerUserId) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);

        List<ExerciseRunInject> injects = runInjectRepo
            .findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(orgId, runId);
        List<InjectView> injectViews = injects.stream()
            .map(i -> toInjectView(orgId, i)).toList();

        // Group responses by run_inject_id for the read-only timeline.
        Map<Long, List<ResponseView>> responsesByInject = new LinkedHashMap<>();
        for (ExerciseRunInject i : injects) {
            List<ResponseView> rs = responseRepo
                .findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(orgId, i.getId())
                .stream().map(ExerciseObserverService::toResponseView).toList();
            if (!rs.isEmpty()) responsesByInject.put(i.getId(), rs);
        }

        List<ObservationView> observations = observationRepo
            .findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(orgId, runId)
            .stream().map(ExerciseObserverService::toObservationView).toList();

        List<AssignmentView> assignments = assignmentRepo
            .findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(orgId, runId)
            .stream().map(ExerciseObserverService::toAssignmentView).toList();

        boolean isAssigned = observerUserId != null && assignmentRepo
            .findByOrganisationIdAndExerciseRunIdAndObserverUserId(orgId, runId, observerUserId)
            .isPresent();

        return new ObserverDashboard(
            summarise(run),
            injectViews,
            responsesByInject,
            observations,
            assignments,
            observerUserId,
            isAssigned
        );
    }

    // ─── Observation capture ────────────────────────────────────────────

    /**
     * Record a new observation. Gated on an active observer assignment for
     * the current user (security principal) on this run. The controller's
     * {@code @PreAuthorize} only enforces the role floor; the assignment
     * check is the per-run gate.
     */
    @Transactional
    public ObservationView recordObservation(Long runId, ObservationRequest req) {
        Long orgId = org();
        Long userId = requireCurrentUserId();
        ExerciseRun run = loadRun(runId, orgId);
        requireAssignedObserver(orgId, runId, userId);

        String type = normaliseType(req.observationType());
        String severity = normaliseSeverity(req.severity());

        String textEn = req.observationTextEn();
        String textAr = req.observationTextAr();
        // At least one of the two languages must be present.
        if ((textEn == null || textEn.isBlank()) && (textAr == null || textAr.isBlank())) {
            throw new IllegalArgumentException(
                "observationTextEn or observationTextAr is required");
        }
        // If only Arabic was supplied, mirror it to EN so the NOT NULL column
        // stays populated — the observer captured in their preferred language.
        if (textEn == null || textEn.isBlank()) textEn = textAr;

        // Optional inject linkage — validate that the inject belongs to this run.
        Long injectId = req.relatedInjectId();
        if (injectId != null) {
            final Long lookupId = injectId;
            ExerciseRunInject inj = runInjectRepo.findByIdAndOrganisationId(lookupId, orgId)
                .filter(i -> Objects.equals(i.getExerciseRunId(), runId))
                .orElseThrow(() -> new EntityNotFoundException(
                    "Inject " + lookupId + " not found on run " + runId));
            injectId = inj.getId();
        }

        ExerciseObservation o = new ExerciseObservation();
        o.setOrganisationId(orgId);
        o.setExerciseRunId(run.getId());
        o.setObserverUserId(userId);
        o.setRelatedInjectId(injectId);
        o.setObservationTextEn(textEn);
        o.setObservationTextAr(textAr);
        o.setObservationType(type);
        o.setSeverity(severity);
        o.setRecommendationEn(req.recommendationEn());
        o.setRecommendationAr(req.recommendationAr());
        LocalDateTime now = LocalDateTime.now();
        o.setRecordedAt(now);
        o.setTimeOffsetMinutes(computeOffset(run.getStartedAt(), now));
        o.setIsFlaggedForAar(Boolean.TRUE.equals(req.flagForAar()));
        ExerciseObservation saved = observationRepo.save(o);

        audit.log("CREATE", "ExerciseObservation", saved.getId(),
            "Observation recorded on run " + run.getRunCode()
                + " by observer #" + userId
                + " (type=" + type + ", severity=" + severity
                + (saved.getIsFlaggedForAar() ? ", flagged-for-AAR" : "") + ")");

        emit(orgId, "EXERCISE_OBSERVATION_RECORDED", "ExerciseObservation", saved.getId(), Map.of(
            "runId",            String.valueOf(run.getId()),
            "runCode",          run.getRunCode(),
            "observerUserId",   String.valueOf(userId),
            "observationType",  type,
            "severity",         severity,
            "relatedInjectId",  String.valueOf(saved.getRelatedInjectId()),
            "flaggedForAar",    String.valueOf(saved.getIsFlaggedForAar())
        ));

        // Live broadcast — facilitator console + other observers see the new
        // observation immediately. Reuses the 10-B run topic; payload carries
        // both languages so subscribers in either locale can render without
        // a refetch. (Map.ofEntries because Map.of caps at 10 K/V pairs.)
        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.ofEntries(
            Map.entry("type",                "OBSERVATION_ADDED"),
            Map.entry("runId",               String.valueOf(run.getId())),
            Map.entry("observationId",       String.valueOf(saved.getId())),
            Map.entry("observerUserId",      String.valueOf(userId)),
            Map.entry("observationType",     type),
            Map.entry("severity",            severity),
            Map.entry("relatedInjectId",     String.valueOf(saved.getRelatedInjectId())),
            Map.entry("observationTextEn",   nullSafe(saved.getObservationTextEn())),
            Map.entry("observationTextAr",   nullSafe(saved.getObservationTextAr())),
            Map.entry("flaggedForAar",       String.valueOf(saved.getIsFlaggedForAar())),
            Map.entry("timeOffsetMinutes",   String.valueOf(saved.getTimeOffsetMinutes()))
        ));

        return toObservationView(saved);
    }

    /** Toggle the AAR flag on an observation. Only the observation's author can flag. */
    @Transactional
    public ObservationView flagObservationForAar(Long runId, Long observationId, FlagRequest req) {
        Long orgId = org();
        Long userId = requireCurrentUserId();
        ExerciseRun run = loadRun(runId, orgId);

        ExerciseObservation o = observationRepo.findByIdAndOrganisationId(observationId, orgId)
            .filter(x -> Objects.equals(x.getExerciseRunId(), runId))
            .orElseThrow(() -> new EntityNotFoundException(
                "Observation " + observationId + " not found on run " + runId));

        // Only the original observer can flag their own observation. (Auditors
        // and BCM_MANAGER can read but cannot rewrite another observer's flag.)
        if (!Objects.equals(o.getObserverUserId(), userId)) {
            throw new AccessDeniedException(
                "Only the observation author can change the AAR flag");
        }

        boolean prior = Boolean.TRUE.equals(o.getIsFlaggedForAar());
        boolean target = Boolean.TRUE.equals(req.flagged());
        if (prior == target) {
            return toObservationView(o);
        }
        o.setIsFlaggedForAar(target);
        ExerciseObservation saved = observationRepo.save(o);

        audit.log("UPDATE", "ExerciseObservation", saved.getId(),
            "Observation " + saved.getId() + " AAR flag " + (target ? "set" : "cleared")
                + " on run " + run.getRunCode());

        emit(orgId, "EXERCISE_OBSERVATION_FLAGGED", "ExerciseObservation", saved.getId(), Map.of(
            "runId",         String.valueOf(run.getId()),
            "observationId", String.valueOf(saved.getId()),
            "flaggedForAar", String.valueOf(target)
        ));

        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.of(
            "type",          "OBSERVATION_FLAGGED",
            "runId",         String.valueOf(run.getId()),
            "observationId", String.valueOf(saved.getId()),
            "flaggedForAar", String.valueOf(target)
        ));

        return toObservationView(saved);
    }

    @Transactional(readOnly = true)
    public List<ObservationView> listObservations(Long runId) {
        Long orgId = org();
        loadRun(runId, orgId);
        return observationRepo
            .findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(orgId, runId)
            .stream().map(ExerciseObserverService::toObservationView).toList();
    }

    /**
     * End-of-run scoring view: observations grouped by type. Useful for the
     * scoring screen where the auditor walks through every STRENGTH, every
     * GAP, every RISK in turn.
     */
    @Transactional(readOnly = true)
    public ObservationsByType getObservationsByType(Long runId) {
        Long orgId = org();
        loadRun(runId, orgId);
        List<ObservationView> all = observationRepo
            .findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(orgId, runId)
            .stream().map(ExerciseObserverService::toObservationView).toList();
        Map<String, List<ObservationView>> grouped = new LinkedHashMap<>();
        // Stable type order matches the dashboard's filter rail.
        for (String t : List.of("STRENGTH", "GAP", "RISK", "COMMUNICATION",
                                "DECISION_QUALITY", "ESCALATION", "RECOVERY_TIME",
                                "COMPLIANCE", "GENERAL")) {
            List<ObservationView> bucket = all.stream()
                .filter(o -> t.equals(o.observationType()))
                .collect(Collectors.toList());
            if (!bucket.isEmpty()) grouped.put(t, bucket);
        }
        return new ObservationsByType(runId, grouped);
    }

    // ─── AAR integration hook (called by ExerciseRuntimeService.endRun) ─

    /**
     * Build the "Observer findings" section of the After-Action Report,
     * pulling all observations where {@code is_flagged_for_aar = 1} on the
     * given run, grouped by type then severity, with the observer role
     * annotated per row.
     *
     * <p>Called by
     * {@link com.tss.grc.module.bcm.cycle.runtime.service.ExerciseRuntimeService}'s
     * private {@code createAfterActionDraft} after the run is COMPLETED.
     *
     * <p>Returns the empty string when there are no flagged observations
     * (so the caller can skip the section entirely).
     */
    @Transactional(readOnly = true)
    public String buildAarSection(Long orgId, Long runId) {
        List<ExerciseObservation> flagged = observationRepo
            .findByOrganisationIdAndExerciseRunIdAndIsFlaggedForAarOrderByRecordedAtAsc(
                orgId, runId, Boolean.TRUE);
        if (flagged.isEmpty()) return "";

        // Build observer role lookup so AAR rows can be annotated by who flagged them.
        Map<Long, String> roleByUser = assignmentRepo
            .findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(orgId, runId)
            .stream().collect(Collectors.toMap(
                ExerciseObserverAssignment::getObserverUserId,
                ExerciseObserverAssignment::getObserverRole,
                (a, b) -> a));   // keep first on duplicate

        // Group by type (stable order)
        Map<String, List<ExerciseObservation>> byType = new LinkedHashMap<>();
        for (String t : List.of("STRENGTH", "GAP", "RISK", "COMMUNICATION",
                                "DECISION_QUALITY", "ESCALATION", "RECOVERY_TIME",
                                "COMPLIANCE", "GENERAL")) {
            List<ExerciseObservation> bucket = flagged.stream()
                .filter(o -> t.equals(o.getObservationType()))
                .toList();
            if (!bucket.isEmpty()) byType.put(t, bucket);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n== Observer Findings (flagged for AAR) ==\n");
        sb.append("Total flagged observations: ").append(flagged.size()).append("\n\n");

        for (Map.Entry<String, List<ExerciseObservation>> e : byType.entrySet()) {
            sb.append("--- ").append(e.getKey())
              .append(" (").append(e.getValue().size()).append(") ---\n");
            for (ExerciseObservation o : e.getValue()) {
                String role = roleByUser.getOrDefault(o.getObserverUserId(), "OBSERVER");
                sb.append("  [").append(o.getSeverity()).append("] ")
                  .append("T+").append(o.getTimeOffsetMinutes()).append("m — ")
                  .append("observer #").append(o.getObserverUserId())
                  .append(" (").append(role).append("): ")
                  .append(truncate(o.getObservationTextEn(), 280));
                if (o.getRecommendationEn() != null && !o.getRecommendationEn().isBlank()) {
                    sb.append("\n     Rec: ").append(truncate(o.getRecommendationEn(), 200));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private ExerciseRun loadRun(Long runId, Long orgId) {
        return runRepo.findByIdAndOrganisationId(runId, orgId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Exercise run " + runId + " not found for tenant " + orgId));
    }

    private void requireAssignedObserver(Long orgId, Long runId, Long userId) {
        assignmentRepo
            .findByOrganisationIdAndExerciseRunIdAndObserverUserId(orgId, runId, userId)
            .orElseThrow(() -> new AccessDeniedException(
                "User #" + userId + " is not an assigned observer on run " + runId));
    }

    private static Long requireCurrentUserId() {
        Long uid = currentUserId();
        if (uid == null) {
            throw new AccessDeniedException(
                "No authenticated user — cannot record observation");
        }
        return uid;
    }

    private static String normaliseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("observerRole is required");
        }
        String r = role.trim().toUpperCase(Locale.ROOT);
        if (!OBSERVER_ROLES.contains(r)) {
            throw new IllegalArgumentException(
                "observerRole must be one of " + OBSERVER_ROLES + " (was " + role + ")");
        }
        return r;
    }

    private static String normaliseType(String type) {
        if (type == null || type.isBlank()) return "GENERAL";
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (!OBSERVATION_TYPES.contains(t)) {
            throw new IllegalArgumentException(
                "observationType must be one of " + OBSERVATION_TYPES + " (was " + type + ")");
        }
        return t;
    }

    private static String normaliseSeverity(String severity) {
        if (severity == null || severity.isBlank()) return "INFO";
        String s = severity.trim().toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(s)) {
            throw new IllegalArgumentException(
                "severity must be one of " + SEVERITIES + " (was " + severity + ")");
        }
        return s;
    }

    private static int computeOffset(LocalDateTime startedAt, LocalDateTime at) {
        if (startedAt == null || at == null) return 0;
        long mins = Duration.between(startedAt, at).toMinutes();
        return (int) Math.max(0, Math.min(mins, Integer.MAX_VALUE));
    }

    private void emit(Long orgId, String trigger, String entityType, Long entityId,
                      Map<String, String> payload) {
        Map<String, Object> p = new HashMap<>(payload);
        publisher.publishEvent(new DomainEvent(
            orgId, trigger, entityType, entityId,
            "exercise-run:" + payload.get("runId"), p));
        log.debug("emitted {} for {} #{}", trigger, entityType, entityId);
    }

    private RunSummary summarise(ExerciseRun r) {
        Long orgId = r.getOrganisationId();
        long pending      = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "PENDING");
        long dispatched   = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "DISPATCHED");
        long acknowledged = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "ACKNOWLEDGED");
        long completed    = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "COMPLETED");
        long skipped      = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "SKIPPED");
        long total        = pending + dispatched + acknowledged + completed + skipped;
        long dispatchedOrMore = dispatched + acknowledged + completed;
        return new RunSummary(
            r.getId(), r.getExerciseId(), r.getRunCode(), r.getStatus(), r.getChannel(),
            r.getFacilitatorUserId(), r.getParticipantCount(),
            r.getStartedAt(), r.getEndedAt(), r.getCreatedAt(),
            (int) total, (int) dispatchedOrMore, (int) pending
        );
    }

    private InjectView toInjectView(Long orgId, ExerciseRunInject i) {
        long responseCount = responseRepo.countByOrganisationIdAndRunInjectId(orgId, i.getId());
        return new InjectView(
            i.getId(), i.getSourceInjectId(),
            i.getPlannedTimeOffsetMinutes(), i.getActualDispatchedAt(),
            i.getInjectType(), i.getInjectTextEn(), i.getInjectTextAr(),
            i.getExpectedActionEn(), i.getExpectedActionAr(),
            i.getStatus(), (int) responseCount
        );
    }

    private static ResponseView toResponseView(ExerciseRunResponse r) {
        return new ResponseView(
            r.getId(), r.getRunInjectId(), r.getParticipantUserId(),
            r.getResponseTextEn(), r.getResponseTextAr(),
            r.getDecisionMadeEn(), r.getDecisionMadeAr(),
            r.getResponseAt(), r.getResponseTimeSeconds(),
            r.getQualityScore(),
            r.getFacilitatorCommentEn(), r.getFacilitatorCommentAr()
        );
    }

    private static AssignmentView toAssignmentView(ExerciseObserverAssignment a) {
        return new AssignmentView(
            a.getId(), a.getExerciseRunId(), a.getObserverUserId(),
            a.getObserverRole(), a.getAssignedByUserId(), a.getAssignedAt()
        );
    }

    private static ObservationView toObservationView(ExerciseObservation o) {
        return new ObservationView(
            o.getId(), o.getExerciseRunId(), o.getObserverUserId(),
            o.getRelatedInjectId(),
            o.getObservationTextEn(), o.getObservationTextAr(),
            o.getObservationType(), o.getSeverity(),
            o.getRecommendationEn(), o.getRecommendationAr(),
            o.getRecordedAt(), o.getTimeOffsetMinutes(),
            Boolean.TRUE.equals(o.getIsFlaggedForAar())
        );
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // Suppress IDE warnings for unused fields in some test paths
    @SuppressWarnings("unused")
    private static List<ObservationView> empty() { return new ArrayList<>(); }
}
