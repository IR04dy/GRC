package com.tss.grc.module.bcm.cycle.runtime.service;

import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.security.EntityOwnershipResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;

/**
 * F2-161 / ADR-017 D-14 — the second {@link EntityOwnershipResolver}: who facilitates an exercise run.
 *
 * <p>The mechanism arrived with F2-159 ({@code ML_MODEL}); this is the case that proves it generalises,
 * and the reason F2-52 refused to seat a {@code FACILITATOR} role. {@code bcm.exercise_run
 * .facilitator_user_id} is populated on <b>4 of 4</b> rows, so the platform already knows who runs each
 * exercise. A role would be looser: the facilitator of run 1 could drive run 4, which during a live
 * exercise is the one thing the runtime must not allow.
 *
 * <h3>⚠ What this measurably restores, and it is worse than the model case</h3>
 * <p>The runtime's write guards are {@code hasAnyRole('FACILITATOR','BCM_MANAGER','PLATFORM_ADMIN')}.
 * {@code FACILITATOR} matches no row in {@code iam.roles} and {@code BCM_MANAGER} has <b>0 users</b>, so
 * dispatching an inject, adding a custom inject, transitioning the run and assigning an observer were
 * reachable by the <b>platform administrator and nobody else</b>. All four dev runs are facilitated by
 * {@code rmanager}, who could read them (RISK_MANAGER is on the read guards) and could not drive them.
 * For a business-continuity tool that is the live-exercise path — the screen someone is on while the
 * clock runs.
 *
 * <h3>Why the observation endpoints are bound too</h3>
 * <p>Recording, flagging and promoting an observation are findings <em>about</em> the exercise the
 * facilitator ran, which is normally the V367 self-review shape. Two measured reasons they are safe here:
 * {@code flagObservationForAar} already enforces authorship <b>in the service</b> — <i>"Only the
 * observation author can change the AAR flag"</i> — so the guard is a coarse outer gate over a real
 * inner control; and {@code promoteObservationToGap} creates a tracked {@code BcmGap}, which is
 * self-incriminating rather than self-serving. {@code removeObserver} soft-deletes the assignment only
 * and audit-logs it; observations survive it, so it cannot erase a finding.
 */
@Component
@RequiredArgsConstructor
public class ExerciseRunOwnershipResolver implements EntityOwnershipResolver {

    /** As written in the guards: {@code hasPermission(#runId, 'EXERCISE_RUN', 'OWNER')}. */
    public static final String TARGET_TYPE = "EXERCISE_RUN";

    private final ExerciseRunRepository repository;

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    /**
     * <p>⚠ The id arrives as a {@code Long} from {@code @PathVariable Long id} on one controller and
     * {@code Long runId} on the other, but a guard is a string expression and nothing forces that — so
     * both the numeric and the textual form are accepted, and anything else is <b>not owned</b> rather
     * than an exception. Throwing here would turn a failed ownership leg into a 500 on an endpoint the
     * caller may legitimately reach by role.
     *
     * <p>Scoped by {@code organisationId} via the repository's own org-scoped finder: this is the one
     * place a raw client-supplied identifier is resolved before any other authorisation has run.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Serializable id, Long userId, Long organisationId) {
        Long runId = parse(id);
        if (runId == null) return false;
        return repository.findByIdAndOrganisationId(runId, organisationId)
            .map(run -> userId.equals(run.getFacilitatorUserId()))
            .orElse(false);
    }

    private static Long parse(Serializable id) {
        if (id instanceof Long l) return l;
        if (id instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(id.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
