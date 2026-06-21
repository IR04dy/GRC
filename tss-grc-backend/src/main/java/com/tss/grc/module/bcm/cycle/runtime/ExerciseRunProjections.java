package com.tss.grc.module.bcm.cycle.runtime;

import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.InjectView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.ResponseView;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunResponse;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;

/**
 * Shared runtime-projection helpers for the BCM exercise-runtime slice
 * (10-B {@code ExerciseRuntimeService} + 10-C
 * {@code ExerciseObserverService}).
 *
 * <p>Both the facilitator console service and the observer-dashboard service
 * project the same run-time entities into the same response DTOs
 * ({@link RunSummary}, {@link InjectView}, {@link ResponseView}). Previously
 * each service carried byte-identical copies of {@code summarise},
 * {@code toInjectView}, {@code toResponseView}, {@code nullSafe}, and
 * {@code truncate}; this class is the single source of truth for that mapping
 * so the two services cannot drift.
 *
 * <p>Stateless, static-only utility — the count queries it needs are taken as
 * explicit repository parameters so it carries no Spring-managed state and the
 * callers keep their existing constructor signatures (and their unit tests'
 * {@code @InjectMocks} wiring) unchanged.
 *
 * <p>Methods are {@code public} only because the two consuming services live in
 * sibling sub-packages ({@code ...runtime.service} and
 * {@code ...runtime.observer.service}); the class is otherwise an internal
 * implementation detail of the runtime slice.
 */
public final class ExerciseRunProjections {

    private ExerciseRunProjections() {}

    /**
     * Project a run header into the list/summary DTO, counting injects by
     * status. {@code totalInjects} is the sum of every status bucket;
     * {@code dispatchedInjects} counts DISPATCHED + ACKNOWLEDGED + COMPLETED
     * (i.e. "dispatched or further along"); {@code pendingInjects} counts
     * PENDING only.
     */
    public static RunSummary summarise(ExerciseRunInjectRepository runInjectRepo, ExerciseRun r) {
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

    /** Project a single run-inject into its view, counting its responses. */
    public static InjectView toInjectView(ExerciseRunResponseRepository responseRepo, Long orgId, ExerciseRunInject i) {
        long responseCount = responseRepo.countByOrganisationIdAndRunInjectId(orgId, i.getId());
        return new InjectView(
            i.getId(), i.getSourceInjectId(),
            i.getPlannedTimeOffsetMinutes(), i.getActualDispatchedAt(),
            i.getInjectType(), i.getInjectTextEn(), i.getInjectTextAr(),
            i.getExpectedActionEn(), i.getExpectedActionAr(),
            i.getStatus(), (int) responseCount
        );
    }

    /** Project a single participant response into its view. */
    public static ResponseView toResponseView(ExerciseRunResponse r) {
        return new ResponseView(
            r.getId(), r.getRunInjectId(), r.getParticipantUserId(),
            r.getResponseTextEn(), r.getResponseTextAr(),
            r.getDecisionMadeEn(), r.getDecisionMadeAr(),
            r.getResponseAt(), r.getResponseTimeSeconds(),
            r.getQualityScore(),
            r.getFacilitatorCommentEn(), r.getFacilitatorCommentAr()
        );
    }

    /** {@code null} → empty string. For WS payload fields that must be non-null. */
    public static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Truncate to {@code max} chars (ellipsis-suffixed when over), returning
     * {@code null} for null input.
     *
     * <p>The null→null behaviour matches the facilitator console's timeline
     * projection, where a {@code TimelineEntry}'s detail field carries through
     * the source's null verbatim. The observer-findings / AAR call sites only
     * ever pass non-null text (the EN body is mirrored NOT-NULL on capture and
     * the recommendation call is guarded by a non-blank check), so the null
     * branch is never exercised there — making this unification behaviour-
     * preserving for every caller.
     */
    public static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
