package com.tss.grc.module.bcm.cycle.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-161 — which exercise-runtime endpoints admit the run's facilitator, and which cannot.
 *
 * <h3>The failure this forbids, and it is a 500 rather than a 403</h3>
 * <p>The same wide guard text sat on routes that name a run ({@code get /{id}}) and routes that do not
 * ({@code GET /}, {@code by-exercise/{exerciseId}}). Appending {@code or hasPermission(#id, …)} by textual
 * replace would have put {@code #id} into the expression of methods with no such parameter — SpEL cannot
 * resolve it, so the guard throws and the endpoint answers <b>500 on the run list</b>. The conversion had
 * to be done per line, and this test pins the result so a later "tidy-up" cannot re-merge the constants.
 *
 * <p>Three routes are unbindable for reasons that are about the data, not the code: {@code POST /} starts
 * a run that does not exist yet, {@code GET /} is a collection, and {@code by-exercise/{exerciseId}} is
 * scoped by exercise — one exercise has many runs, possibly with different facilitators, so the exercise
 * id cannot answer "do you facilitate this run".
 */
class ExerciseRunOwnershipGuardTest {

    private static final String LEG_ID = "hasPermission(#id, 'EXERCISE_RUN', 'OWNER')";
    private static final String LEG_RUN = "hasPermission(#runId, 'EXERCISE_RUN', 'OWNER')";

    /** Run-scoped routes: the facilitator drives their own exercise. */
    private static final Map<String, String> MUST_ADMIT = Map.ofEntries(
        Map.entry("ExerciseRuntimeController#get", LEG_ID),
        Map.entry("ExerciseRuntimeController#timeline", LEG_ID),
        Map.entry("ExerciseRuntimeController#listResponses", LEG_ID),
        Map.entry("ExerciseRuntimeController#dispatch", LEG_ID),
        Map.entry("ExerciseRuntimeController#customInject", LEG_ID),
        Map.entry("ExerciseRuntimeController#transition", LEG_ID),
        Map.entry("ExerciseObserverController#assignObserver", LEG_RUN),
        Map.entry("ExerciseObserverController#removeObserver", LEG_RUN),
        Map.entry("ExerciseObserverController#listObservers", LEG_RUN),
        Map.entry("ExerciseObserverController#getObserverDashboard", LEG_RUN),
        Map.entry("ExerciseObserverController#recordObservation", LEG_RUN),
        Map.entry("ExerciseObserverController#flagObservation", LEG_RUN),
        Map.entry("ExerciseObserverController#promoteObservationToGap", LEG_RUN),
        Map.entry("ExerciseObserverController#listObservations", LEG_RUN),
        Map.entry("ExerciseObserverController#observationsByType", LEG_RUN));

    /**
     * Routes that must carry <b>no</b> ownership leg. Binding any of them is not merely wrong policy —
     * {@code #id} and {@code #runId} do not exist on these methods, so the guard would fail to evaluate
     * and the endpoint would answer 500.
     */
    private static final Set<String> MUST_NOT_ADMIT = Set.of(
        "ExerciseRuntimeController#start",
        "ExerciseRuntimeController#list",
        "ExerciseRuntimeController#listForExercise");

    private static TreeMap<String, String> runtimeGuards() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        TreeMap<String, String> out = new TreeMap<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module.bcm.cycle.runtime")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {                                              // NOPMD
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa != null) out.put(c.getSimpleName() + "#" + m.getName(), pa.value());
            }
        }
        return out;
    }

    @Test
    void everyRunScopedRouteAdmitsItsFacilitatorAndNoOtherRouteDoes() {
        TreeMap<String, String> guards = runtimeGuards();
        assertThat(guards.size())
            .as("scanned %d guarded methods under module.bcm.cycle.runtime; the two controllers carry "
                + "18. A collapsed scan proves nothing.", guards.size())
            .isGreaterThanOrEqualTo(18);

        List<String> missing = new ArrayList<>();
        MUST_ADMIT.forEach((site, leg) -> {
            String g = guards.get(site);
            if (g == null) {
                missing.add(site + " — NOT FOUND (renamed or removed; this test must follow it, because "
                    + "a check that cannot find its subject passes silently)");
            } else if (!g.contains(leg)) {
                missing.add(site + " — expected " + leg + " but guard is: " + g);
            }
        });
        assertThat(missing).as("""
            These routes name a single exercise run and must admit that run's facilitator. Without the \
            leg the runtime falls back to BCM_MANAGER (0 users in dev) and PLATFORM_ADMIN, which is how \
            dispatching an inject became administrator-only while `rmanager` facilitated all four runs.

            ⚠ Note the parameter name differs by controller: #id on the runtime, #runId on the observer \
            console. A copied guard constant is the likely way this breaks.

            Offenders: %s""".formatted(missing)).isEmpty();

        List<String> overreach = new ArrayList<>();
        for (String site : MUST_NOT_ADMIT) {
            String g = guards.get(site);
            if (g == null) {
                overreach.add(site + " — NOT FOUND (see above: a missing subject is a silent pass)");
            } else if (g.contains("EXERCISE_RUN")) {
                overreach.add(site + " -> " + g);
            }
        }
        assertThat(overreach).as("""
            These routes name NO single run, so an ownership leg cannot be evaluated: SpEL has no #id or \
            #runId to resolve and the endpoint answers 500, not 403. `POST /` starts a run that does not \
            exist yet, `GET /` is a collection, and `by-exercise/{exerciseId}` is scoped by exercise — \
            one exercise has many runs, possibly with different facilitators.

            Offenders: %s""".formatted(overreach)).isEmpty();
    }

    /**
     * An ownership check that REPLACED the oversight leg would read as a tightening and would lock the
     * people accountable for an exercise out of it — F2-53's direction.
     *
     * <p><b>F2-254 — this leg's DETECTOR was {@code !guard.contains("hasAnyRole(")} while its CLAIM is
     * that an oversight leg EXISTS.</b> Those are different assertions, and the difference surfaced the
     * moment the oversight leg stopped being spelled with role names: {@code #dispatch},
     * {@code #customInject} and {@code #transition} were
     * {@code hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')},
     * and {@code BCM_MANAGER} is held by <b>no user</b> in dev or prod — so the "oversight" the old
     * detector was checking for admitted the platform administrator and nobody else, while
     * {@code hasPermission('BCM','WRITE')} admits three live roles. The gate failed on the change that
     * made its own claim <em>more</em> true. Keyed on the shape instead: an oversight leg is any
     * {@code hasAnyRole(...)} or any <b>module-level</b> {@code hasPermission('MODULE','ACTION')}, which
     * the 3-argument ownership form can never satisfy because its first argument is {@code #id} and not
     * a quoted literal. Deleting the oversight leg entirely still fails, which is the claim.
     */
    @Test
    void everyOwnershipLegSitsBesideAnOversightLeg() {
        List<String> lonely = new ArrayList<>();
        runtimeGuards().forEach((site, guard) -> {
            boolean oversight = guard.contains("hasAnyRole(") || MODULE_PERMISSION.matcher(guard).find();
            if (guard.contains("EXERCISE_RUN") && !oversight) {
                lonely.add(site + " -> " + guard);
            }
        });
        assertThat(lonely).as(
            "the ownership leg is always an additional `or`, never a replacement. Offenders: %s", lonely)
            .isEmpty();
    }

    /**
     * The narrowing above is load-bearing: {@code MODULE_PERMISSION} must NOT match the ownership form,
     * or every owner-only guard would certify itself as its own oversight. Asserted rather than assumed,
     * because a pattern that matched both would make the leg above vacuous and still pass.
     */
    @Test
    void theOversightPatternCannotBeSatisfiedByTheOwnershipLegItself() {
        assertThat(MODULE_PERMISSION.matcher("hasPermission(#id, 'EXERCISE_RUN', 'OWNER')").find())
            .as("the 3-arg ownership form must not read as a module-level permission").isFalse();
        assertThat(MODULE_PERMISSION.matcher("hasPermission(#runId, 'EXERCISE_RUN', 'OWNER')").find())
            .as("nor the observer console's parameter spelling").isFalse();
        assertThat(MODULE_PERMISSION.matcher("hasPermission('BCM','WRITE')").find())
            .as("a module-level permission is oversight").isTrue();
        assertThat(MODULE_PERMISSION.matcher("hasPermission('BCM', 'WRITE')").find())
            .as("and so is the spaced spelling this codebase also uses").isTrue();
    }

    /** A module-level check: first argument is a quoted literal, never a {@code #param}. */
    private static final java.util.regex.Pattern MODULE_PERMISSION =
            java.util.regex.Pattern.compile("hasPermission\\(\\s*'");
}
