package com.tss.grc.module.bcm.cycle.runtime.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.ExerciseScenarioInject;
import com.tss.grc.module.bcm.cycle.repository.ExerciseScenarioInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.CustomInjectRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.InjectView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunDetail;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunTimeline;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.StartRunRequest;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.TimelineEntry;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.TransitionRequest;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunResponse;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Slice 10-B — Exercise Runtime service.
 *
 * <p>Run-time machinery that turns a Slice 10-A pre-staged
 * {@link BcmExercise} into a live facilitator session.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Start / pause / resume / end / abort a run (state machine).</li>
 *   <li>Clone library injects onto the run at start, denormalising the body
 *       so the historical timeline is immutable to library edits.</li>
 *   <li>Dispatch injects on schedule or on demand; broadcast the dispatched
 *       inject over STOMP for live participants and facilitator console
 *       subscribers.</li>
 *   <li>Capture participant responses with computed response-time-in-seconds.</li>
 *   <li>Allow the facilitator to inject on-the-fly "CUSTOM" items mid-run.</li>
 *   <li>Build the chronological after-action timeline for the AAR module.</li>
 *   <li>Audit every state change + dispatch + response (F7) and emit the
 *       matching domain event (F8).</li>
 * </ul>
 *
 * <p>WebSocket topic shape — every dispatch + response broadcast follows
 * the platform convention {@code /topic/org.{orgId}.exercise-run.{runId}}
 * via {@link RealtimeBroadcaster#broadcastOrgEvent}. The frontend
 * facilitator console + participant view subscribe to that topic.
 */
@Service
@RequiredArgsConstructor
public class ExerciseRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseRuntimeService.class);

    /** Permitted state transitions on the run. */
    static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "SCHEDULED",   Set.of("IN_PROGRESS", "ABORTED"),
        "IN_PROGRESS", Set.of("PAUSED", "COMPLETED", "ABORTED"),
        "PAUSED",      Set.of("IN_PROGRESS", "COMPLETED", "ABORTED"),
        "COMPLETED",   Set.of(),
        "ABORTED",     Set.of()
    );

    /** Valid {@code channel} values. */
    static final Set<String> CHANNELS = Set.of("IN_PERSON", "VIRTUAL_MEET", "HYBRID", "CHAT_ONLY");

    /** Valid run-inject types. */
    static final Set<String> INJECT_TYPES = Set.of(
        "INFORMATION", "ESCALATION", "DECISION", "MEDIA", "REGULATOR", "CUSTOM");

    private final ExerciseRunRepository runRepo;
    private final ExerciseRunInjectRepository runInjectRepo;
    private final ExerciseRunResponseRepository responseRepo;
    private final BcmExerciseRepository exerciseRepo;
    private final ExerciseScenarioInjectRepository scenarioInjectRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final RealtimeBroadcaster realtime;

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Run lifecycle ──────────────────────────────────────────────────

    /**
     * Start a new run.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the parent {@link BcmExercise} and confirm tenant scope.</li>
     *   <li>Build the {@link ExerciseRun} row in IN_PROGRESS, with a
     *       generated {@code run_code} unless the caller supplied one.</li>
     *   <li>Clone every {@link ExerciseScenarioInject} attached to the
     *       scenario into a fresh {@link ExerciseRunInject}, with the body
     *       denormalised so editing the library scenario later does not
     *       rewrite the historical timeline. If the exercise has no
     *       linked scenario, the run starts with no injects (custom-only).</li>
     *   <li>F7 audit + F8 {@code EXERCISE_RUN_STARTED} event.</li>
     * </ol>
     */
    @Transactional
    public RunSummary startRun(StartRunRequest req) {
        Long orgId = org();
        BcmExercise ex = exerciseRepo.findById(req.exerciseId())
            .filter(e -> Objects.equals(e.getOrganisationId(), orgId))
            .orElseThrow(() -> new EntityNotFoundException(
                "Exercise " + req.exerciseId() + " not found for tenant " + orgId));

        String channel = normaliseChannel(req.channel());

        ExerciseRun run = new ExerciseRun();
        run.setOrganisationId(orgId);
        run.setExerciseId(ex.getId());
        run.setRunCode(req.runCode() != null && !req.runCode().isBlank()
            ? req.runCode().trim()
            : generateRunCode(orgId, ex.getId()));
        run.setFacilitatorUserId(req.facilitatorUserId());
        run.setStatus("IN_PROGRESS");
        run.setStartedAt(LocalDateTime.now());
        run.setChannel(channel);
        run.setParticipantCount(Objects.requireNonNullElse(req.participantCount(), 0));
        run.setFacilitatorNotesEn(req.facilitatorNotesEn());
        run.setFacilitatorNotesAr(req.facilitatorNotesAr());
        ExerciseRun saved = runRepo.save(run);

        int cloned = 0;
        if (ex.getScenarioId() != null) {
            List<ExerciseScenarioInject> sourceInjects =
                scenarioInjectRepo.findByScenarioIdOrderByInjectOrderAsc(ex.getScenarioId());
            for (ExerciseScenarioInject src : sourceInjects) {
                ExerciseRunInject ri = new ExerciseRunInject();
                ri.setOrganisationId(orgId);
                ri.setExerciseRunId(saved.getId());
                ri.setSourceInjectId(src.getId());
                ri.setPlannedTimeOffsetMinutes(
                    Objects.requireNonNullElse(src.getTimeOffsetMinutes(), 0));
                ri.setInjectType(src.getInjectType());
                ri.setInjectTextEn(src.getInjectTextEn());
                ri.setInjectTextAr(src.getInjectTextAr());
                ri.setExpectedActionEn(src.getExpectedActionEn());
                ri.setExpectedActionAr(src.getExpectedActionAr());
                ri.setStatus("PENDING");
                runInjectRepo.save(ri);
                cloned++;
            }
        }

        audit.log("CREATE", "ExerciseRun", saved.getId(),
            "Exercise run " + saved.getRunCode() + " started for exercise "
                + ex.getId() + " (" + cloned + " injects cloned, channel=" + channel + ")");

        emit(orgId, "EXERCISE_RUN_STARTED", "ExerciseRun", saved.getId(), Map.of(
            "exerciseId", String.valueOf(ex.getId()),
            "runId",      String.valueOf(saved.getId()),
            "runCode",    saved.getRunCode(),
            "channel",    channel,
            "injectCount", String.valueOf(cloned),
            "facilitatorUserId", String.valueOf(saved.getFacilitatorUserId())
        ));

        return summarise(saved);
    }

    @Transactional(readOnly = true)
    public RunDetail get(Long runId) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        List<ExerciseRunInject> injects = runInjectRepo
            .findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(orgId, runId);
        List<InjectView> views = injects.stream().map(i -> toInjectView(orgId, i)).toList();
        return new RunDetail(summarise(run), run.getFacilitatorNotesEn(),
            run.getFacilitatorNotesAr(), views);
    }

    @Transactional(readOnly = true)
    public List<RunSummary> listForExercise(Long exerciseId) {
        Long orgId = org();
        return runRepo.findByOrganisationIdAndExerciseIdOrderByCreatedAtDesc(orgId, exerciseId)
            .stream().map(this::summarise).toList();
    }

    /**
     * Transition the run. {@code action} is one of PAUSE / RESUME / END / ABORT,
     * which map to the state machine targets PAUSED / IN_PROGRESS / COMPLETED /
     * ABORTED. END writes the optional summary into {@code facilitator_notes_*}.
     */
    @Transactional
    public RunSummary transition(Long runId, String action, TransitionRequest req) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        String from = run.getStatus();
        String to = mapAction(action);

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }

        run.setStatus(to);
        if ("COMPLETED".equals(to) || "ABORTED".equals(to)) {
            run.setEndedAt(LocalDateTime.now());
            if (req != null) {
                if (req.summaryEn() != null && !req.summaryEn().isBlank()) {
                    run.setFacilitatorNotesEn(append(run.getFacilitatorNotesEn(), req.summaryEn()));
                }
                if (req.summaryAr() != null && !req.summaryAr().isBlank()) {
                    run.setFacilitatorNotesAr(append(run.getFacilitatorNotesAr(), req.summaryAr()));
                }
            }
        }
        ExerciseRun saved = runRepo.save(run);

        audit.log("UPDATE", "ExerciseRun", saved.getId(),
            "Exercise run " + saved.getRunCode() + " transitioned " + from + " → " + to);

        String trigger = switch (to) {
            case "PAUSED"      -> "EXERCISE_RUN_PAUSED";
            case "IN_PROGRESS" -> "EXERCISE_RUN_RESUMED";
            case "COMPLETED"   -> "EXERCISE_RUN_COMPLETED";
            case "ABORTED"     -> "EXERCISE_RUN_ABORTED";
            default            -> "EXERCISE_RUN_TRANSITIONED";
        };
        emit(orgId, trigger, "ExerciseRun", saved.getId(), Map.of(
            "runId",   String.valueOf(saved.getId()),
            "runCode", saved.getRunCode(),
            "from",    from,
            "to",      to
        ));

        // Broadcast the lifecycle change to live subscribers
        realtime.broadcastOrgEvent(orgId, "exercise-run." + saved.getId(), Map.of(
            "type",  "STATE_CHANGE",
            "from",  from,
            "to",    to,
            "runId", String.valueOf(saved.getId())
        ));

        // On COMPLETED, draft an after-action report row pre-populated with the
        // run timeline so the AAR module has somewhere to land. Errors are
        // logged but do NOT roll back the transition — the after-action draft
        // is a courtesy, not a hard dependency.
        if ("COMPLETED".equals(to)) {
            try {
                createAfterActionDraft(orgId, saved);
            } catch (RuntimeException ignored) {
                log.warn("AAR draft creation for run {} failed but transition stands", saved.getId());
            }
        }
        return summarise(saved);
    }

    // ─── Inject dispatch + custom inject ────────────────────────────────

    @Transactional
    public InjectView dispatchInject(Long runId, Long runInjectId) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        requireActive(run);

        ExerciseRunInject inj = runInjectRepo.findByIdAndOrganisationId(runInjectId, orgId)
            .filter(i -> Objects.equals(i.getExerciseRunId(), runId))
            .orElseThrow(() -> new EntityNotFoundException(
                "Run inject " + runInjectId + " not found on run " + runId));

        if (!"PENDING".equals(inj.getStatus())) {
            throw new IllegalTransitionException(inj.getStatus(), "DISPATCHED", Set.of("PENDING"));
        }
        inj.setActualDispatchedAt(LocalDateTime.now());
        inj.setStatus("DISPATCHED");
        ExerciseRunInject saved = runInjectRepo.save(inj);

        audit.log("UPDATE", "ExerciseRunInject", saved.getId(),
            "Inject dispatched on run " + run.getRunCode() + " (type=" + saved.getInjectType()
                + ", planned=T+" + saved.getPlannedTimeOffsetMinutes() + "m)");

        emit(orgId, "EXERCISE_INJECT_DISPATCHED", "ExerciseRunInject", saved.getId(), Map.of(
            "runId",                String.valueOf(run.getId()),
            "runCode",              run.getRunCode(),
            "runInjectId",          String.valueOf(saved.getId()),
            "sourceInjectId",       String.valueOf(saved.getSourceInjectId()),
            "injectType",           String.valueOf(saved.getInjectType()),
            "plannedOffsetMinutes", String.valueOf(saved.getPlannedTimeOffsetMinutes())
        ));

        // Live broadcast — participants and facilitators on the run topic see this
        // immediately. Payload carries both languages so subscribers in either
        // locale can render without a refetch.
        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.of(
            "type",                 "INJECT_DISPATCHED",
            "runId",                String.valueOf(run.getId()),
            "runInjectId",          String.valueOf(saved.getId()),
            "injectType",           String.valueOf(saved.getInjectType()),
            "injectTextEn",         nullSafe(saved.getInjectTextEn()),
            "injectTextAr",         nullSafe(saved.getInjectTextAr()),
            "expectedActionEn",     nullSafe(saved.getExpectedActionEn()),
            "expectedActionAr",     nullSafe(saved.getExpectedActionAr()),
            "plannedOffsetMinutes", String.valueOf(saved.getPlannedTimeOffsetMinutes()),
            "dispatchedAt",         String.valueOf(saved.getActualDispatchedAt())
        ));
        return toInjectView(orgId, saved);
    }

    @Transactional
    public InjectView addCustomInject(Long runId, CustomInjectRequest req) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        requireActive(run);

        String type = (req.injectType() == null || req.injectType().isBlank())
            ? "CUSTOM"
            : req.injectType().trim().toUpperCase(Locale.ROOT);
        if (!INJECT_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                "injectType must be one of " + INJECT_TYPES + " (was " + type + ")");
        }

        Integer planned = req.plannedTimeOffsetMinutes();
        if (planned == null) {
            // Default to elapsed minutes since the run started, clamped >= 0.
            if (run.getStartedAt() != null) {
                long mins = Duration.between(run.getStartedAt(), LocalDateTime.now()).toMinutes();
                planned = (int) Math.max(0, mins);
            } else {
                planned = 0;
            }
        }

        ExerciseRunInject ri = new ExerciseRunInject();
        ri.setOrganisationId(orgId);
        ri.setExerciseRunId(run.getId());
        ri.setSourceInjectId(null);   // hallmark of a custom inject
        ri.setPlannedTimeOffsetMinutes(planned);
        ri.setInjectType(type);
        ri.setInjectTextEn(req.injectTextEn());
        ri.setInjectTextAr(req.injectTextAr());
        ri.setExpectedActionEn(req.expectedActionEn());
        ri.setExpectedActionAr(req.expectedActionAr());
        ri.setStatus("PENDING");
        ExerciseRunInject saved = runInjectRepo.save(ri);

        audit.log("CREATE", "ExerciseRunInject", saved.getId(),
            "Custom inject added to run " + run.getRunCode() + " (type=" + type + ")");

        emit(orgId, "EXERCISE_CUSTOM_INJECT_ADDED", "ExerciseRunInject", saved.getId(), Map.of(
            "runId",       String.valueOf(run.getId()),
            "runCode",     run.getRunCode(),
            "runInjectId", String.valueOf(saved.getId()),
            "injectType",  type
        ));

        boolean dispatchNow = req.dispatchNow() == null || req.dispatchNow();
        if (dispatchNow) {
            return dispatchInject(run.getId(), saved.getId());
        }
        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.of(
            "type",        "CUSTOM_INJECT_ADDED",
            "runId",       String.valueOf(run.getId()),
            "runInjectId", String.valueOf(saved.getId()),
            "injectType",  type
        ));
        return toInjectView(orgId, saved);
    }

    // ─── Participant responses ──────────────────────────────────────────

    @Transactional
    public ResponseView recordResponse(Long runId, Long runInjectId, ResponseRequest req) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        requireActive(run);

        ExerciseRunInject inj = runInjectRepo.findByIdAndOrganisationId(runInjectId, orgId)
            .filter(i -> Objects.equals(i.getExerciseRunId(), runId))
            .orElseThrow(() -> new EntityNotFoundException(
                "Run inject " + runInjectId + " not found on run " + runId));

        if (inj.getActualDispatchedAt() == null) {
            throw new IllegalStateException(
                "Cannot record a response — inject " + runInjectId + " has not been dispatched yet");
        }

        ExerciseRunResponse r = new ExerciseRunResponse();
        r.setOrganisationId(orgId);
        r.setRunInjectId(inj.getId());
        r.setParticipantUserId(req.participantUserId());
        r.setResponseTextEn(req.responseTextEn());
        r.setResponseTextAr(req.responseTextAr());
        r.setDecisionMadeEn(req.decisionMadeEn());
        r.setDecisionMadeAr(req.decisionMadeAr());
        LocalDateTime now = LocalDateTime.now();
        r.setResponseAt(now);
        long secs = Math.max(0, Duration.between(inj.getActualDispatchedAt(), now).toSeconds());
        r.setResponseTimeSeconds((int) Math.min(secs, Integer.MAX_VALUE));
        ExerciseRunResponse saved = responseRepo.save(r);

        // Auto-acknowledge the inject on the first response, but never
        // downgrade COMPLETED back to ACKNOWLEDGED.
        if ("DISPATCHED".equals(inj.getStatus())) {
            inj.setStatus("ACKNOWLEDGED");
            runInjectRepo.save(inj);
        }

        audit.log("CREATE", "ExerciseRunResponse", saved.getId(),
            "Response captured on run " + run.getRunCode()
                + " inject " + inj.getId()
                + " by participant " + req.participantUserId()
                + " (" + saved.getResponseTimeSeconds() + "s after dispatch)");

        emit(orgId, "EXERCISE_RESPONSE_RECORDED", "ExerciseRunResponse", saved.getId(), Map.of(
            "runId",              String.valueOf(run.getId()),
            "runInjectId",        String.valueOf(inj.getId()),
            "participantUserId",  String.valueOf(req.participantUserId()),
            "responseTimeSeconds", String.valueOf(saved.getResponseTimeSeconds())
        ));

        realtime.broadcastOrgEvent(orgId, "exercise-run." + run.getId(), Map.of(
            "type",                "RESPONSE_RECORDED",
            "runId",               String.valueOf(run.getId()),
            "runInjectId",         String.valueOf(inj.getId()),
            "responseId",          String.valueOf(saved.getId()),
            "participantUserId",   String.valueOf(req.participantUserId()),
            "responseTimeSeconds", String.valueOf(saved.getResponseTimeSeconds())
        ));
        return toResponseView(saved);
    }

    @Transactional(readOnly = true)
    public List<ResponseView> listResponses(Long runId, Long runInjectId) {
        Long orgId = org();
        loadRun(runId, orgId);   // tenant guard
        return responseRepo
            .findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(orgId, runInjectId)
            .stream().map(ExerciseRuntimeService::toResponseView).toList();
    }

    // ─── Timeline ───────────────────────────────────────────────────────

    /**
     * Build the chronological after-action timeline. Interleaves:
     * <ul>
     *   <li>Inject dispatches (sorted by actual_dispatched_at; un-dispatched
     *       injects appear by planned_time_offset_minutes from started_at).</li>
     *   <li>Participant responses.</li>
     *   <li>Custom injects in their planned slot.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public RunTimeline getRunTimeline(Long runId) {
        Long orgId = org();
        ExerciseRun run = loadRun(runId, orgId);
        List<ExerciseRunInject> injects = runInjectRepo
            .findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(orgId, runId);

        List<TimelineEntry> entries = new ArrayList<>();
        LocalDateTime startedAt = run.getStartedAt();

        for (ExerciseRunInject i : injects) {
            LocalDateTime at = i.getActualDispatchedAt() != null
                ? i.getActualDispatchedAt()
                : (startedAt != null
                    ? startedAt.plusMinutes(Objects.requireNonNullElse(i.getPlannedTimeOffsetMinutes(), 0))
                    : run.getCreatedAt());
            String kind = i.getSourceInjectId() == null ? "CUSTOM_INJECT" : "INJECT_DISPATCHED";
            entries.add(new TimelineEntry(
                at,
                kind,
                i.getId(),
                null,
                i.getPlannedTimeOffsetMinutes(),
                null,
                i.getInjectType(),
                "T+" + i.getPlannedTimeOffsetMinutes() + "m " + i.getInjectType(),
                truncate(i.getInjectTextEn(), 240),
                truncate(i.getInjectTextAr(), 240)
            ));

            List<ExerciseRunResponse> responses = responseRepo
                .findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(orgId, i.getId());
            for (ExerciseRunResponse r : responses) {
                entries.add(new TimelineEntry(
                    r.getResponseAt(),
                    "RESPONSE",
                    i.getId(),
                    r.getId(),
                    i.getPlannedTimeOffsetMinutes(),
                    r.getResponseTimeSeconds(),
                    i.getInjectType(),
                    "Response by user #" + r.getParticipantUserId()
                        + " (" + r.getResponseTimeSeconds() + "s)",
                    truncate(r.getResponseTextEn(), 240),
                    truncate(r.getResponseTextAr(), 240)
                ));
            }
        }

        if (run.getEndedAt() != null) {
            entries.add(new TimelineEntry(
                run.getEndedAt(), "STATE_CHANGE", null, null, null, null, null,
                "Run " + run.getStatus(),
                "Run transitioned to " + run.getStatus(),
                "تحوّل التمرين إلى " + run.getStatus()));
        }

        entries.sort(Comparator.comparing(TimelineEntry::at,
            Comparator.nullsLast(Comparator.naturalOrder())));

        return new RunTimeline(run.getId(), run.getRunCode(), run.getStatus(),
            run.getStartedAt(), run.getEndedAt(), entries);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private ExerciseRun loadRun(Long runId, Long orgId) {
        return runRepo.findByIdAndOrganisationId(runId, orgId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Exercise run " + runId + " not found for tenant " + orgId));
    }

    private void requireActive(ExerciseRun run) {
        if (!"IN_PROGRESS".equals(run.getStatus())) {
            throw new IllegalTransitionException(run.getStatus(), "ACTIVE_OPERATION",
                Set.of("IN_PROGRESS"));
        }
    }

    private String mapAction(String action) {
        if (action == null) throw new IllegalArgumentException("action is required");
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "PAUSE"  -> "PAUSED";
            case "RESUME" -> "IN_PROGRESS";
            case "END"    -> "COMPLETED";
            case "ABORT"  -> "ABORTED";
            default       -> throw new IllegalArgumentException(
                "Unknown action " + action + " (expected PAUSE | RESUME | END | ABORT)");
        };
    }

    private String normaliseChannel(String channel) {
        if (channel == null || channel.isBlank()) return "IN_PERSON";
        String c = channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(c)) {
            throw new IllegalArgumentException(
                "channel must be one of " + CHANNELS + " (was " + channel + ")");
        }
        return c;
    }

    private String generateRunCode(Long orgId, Long exerciseId) {
        long n = runRepo.countByOrganisationIdAndExerciseId(orgId, exerciseId) + 1;
        return String.format("RUN-%d-EX%d-%03d", orgId, exerciseId, n);
    }

    private void emit(Long orgId, String trigger, String entityType, Long entityId,
                      Map<String, String> payload) {
        Map<String, Object> p = new HashMap<>(payload);
        publisher.publishEvent(new DomainEvent(
            orgId, trigger, entityType, entityId,
            "exercise-run:" + payload.get("runId"), p));
        log.debug("emitted {} for {} #{} (run-correlated)", trigger, entityType, entityId);
    }

    private RunSummary summarise(ExerciseRun r) {
        Long orgId = r.getOrganisationId();
        long total      = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "PENDING")
                       + runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "DISPATCHED")
                       + runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "ACKNOWLEDGED")
                       + runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "COMPLETED")
                       + runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "SKIPPED");
        long pending    = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "PENDING");
        long dispatched = runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "DISPATCHED")
                       + runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "ACKNOWLEDGED")
                       + runInjectRepo.countByOrganisationIdAndExerciseRunIdAndStatus(orgId, r.getId(), "COMPLETED");
        return new RunSummary(
            r.getId(), r.getExerciseId(), r.getRunCode(), r.getStatus(), r.getChannel(),
            r.getFacilitatorUserId(), r.getParticipantCount(),
            r.getStartedAt(), r.getEndedAt(), r.getCreatedAt(),
            (int) total, (int) dispatched, (int) pending
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

    private static String append(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        return existing + "\n\n" + addition;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * AAR integration — when a run completes we hand the timeline to the
     * existing AAR module. The exercise AAR is *not* tied to a CrisisEvent
     * (CrisisAar is for real incidents), so we write a structured summary
     * into the parent {@link BcmExercise}'s {@code findings} column as an
     * append-only draft. The Crisis AAR builder picks up exercise-driven
     * AARs through the same {@code findings} field today; this preserves
     * that integration without forking a parallel schema.
     */
    private void createAfterActionDraft(Long orgId, ExerciseRun run) {
        BcmExercise ex = exerciseRepo.findById(run.getExerciseId())
            .filter(e -> Objects.equals(e.getOrganisationId(), orgId))
            .orElse(null);
        if (ex == null) return;
        RunTimeline tl = getRunTimeline(run.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("== Exercise Run After-Action (auto-generated) ==\n");
        sb.append("Run: ").append(run.getRunCode()).append("\n");
        sb.append("Status: ").append(run.getStatus()).append("\n");
        if (run.getStartedAt() != null) sb.append("Started: ").append(run.getStartedAt()).append("\n");
        if (run.getEndedAt() != null)   sb.append("Ended:   ").append(run.getEndedAt()).append("\n");
        sb.append("Timeline entries: ").append(tl.entries().size()).append("\n\n");
        int max = Math.min(40, tl.entries().size());
        for (int i = 0; i < max; i++) {
            TimelineEntry e = tl.entries().get(i);
            sb.append("- [").append(e.kind()).append("] ")
              .append(e.at()).append(" — ")
              .append(e.label() != null ? e.label() : "");
            if (e.responseTimeSeconds() != null) {
                sb.append(" (").append(e.responseTimeSeconds()).append("s)");
            }
            sb.append("\n");
        }
        if (tl.entries().size() > max) {
            sb.append("…\n(").append(tl.entries().size() - max).append(" more entries truncated)\n");
        }

        String prior = ex.getFindings();
        ex.setFindings(prior == null || prior.isBlank()
            ? sb.toString()
            : prior + "\n\n" + sb);
        exerciseRepo.save(ex);

        emit(orgId, "EXERCISE_AAR_DRAFT_CREATED", "BcmExercise", ex.getId(), Map.of(
            "runId",          String.valueOf(run.getId()),
            "runCode",        run.getRunCode(),
            "timelineEntries", String.valueOf(tl.entries().size())
        ));
    }
}
