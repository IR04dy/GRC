package com.tss.grc.module.bcm;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-254 — the {@code module.bcm} block of F2-253's administrator-only guard census.
 *
 * <h3>The defect</h3>
 * {@code hasAnyRole('X')} tests for the granted authority {@code ROLE_X}. A role that is a row in
 * {@code iam.roles} and is assigned to <b>no user</b> grants it to nobody, and a role guard never
 * reaches {@code ModulePermissionEvaluator}, so it never reaches the {@code PLATFORM_ADMIN} bypass
 * either — such an expression passes review only because it happens to NAME the administrator.
 * {@code BCM_MANAGER} is exactly that, in dev and prod alike, and it was the deciding leg of
 * thirteen writes: archiving a BCP binder or a quantitative BIA, starting an exercise run,
 * instantiating a scenario, publishing an MTPD inheritance assessment, the whole
 * process-vendor-dependency register (12 live rows), and five run-scoped writes on the exercise
 * runtime and observer consoles. Probed live as {@code compliance} — who holds {@code BCM:WRITE},
 * {@code BCM:DELETE} <em>and</em> {@code BCM:APPROVE} — all thirteen answered <b>403</b> while
 * {@code admin} reached the handler on every one.
 *
 * <h3>🔑 Why it survived three slices that each looked straight at it</h3>
 * Three separate fixes corrected a <em>ghost</em> inside one of these expressions and left a
 * zero-holder role standing beside it, because "does this name exist?" was the only question any of
 * them asked. F2-52 on {@code SubprocessorController} (closed by F2-253); F2-52 again on
 * {@code BiaQuantitativeController.BOARD_APPROVE_GUARD}, which chose {@code EXECUTIVE_REVIEWER}
 * because it "already holds {@code BCM:APPROVE}" — a fact about GRANTS, where whether a guard admits
 * anybody is a fact about {@code iam.user_roles}; and F2-161 on {@code ExerciseRuntimeController
 * #start}, whose own comment removed {@code FACILITATOR} for matching no row in {@code iam.roles}
 * and "admitting nobody" while keeping {@code BCM_MANAGER}, which admits nobody.
 *
 * <h3>🔑 And why the census could only see eight of the thirteen</h3>
 * {@code ROLE_GUARD_CENSUS.md} files a guard as COMPOSED when the expression MENTIONS
 * {@code hasPermission}, not when the permission is ANDed — F2-253's own third finding, unfixed
 * because splitting that bucket changes a published artefact's schema. Five of these thirteen are
 * {@code hasAnyRole(...) or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')}, so §B2b never listed them
 * and the summary line calls them "already tight". Their ownership leg is real and it rescued the
 * facilitator; it did not rescue anybody else, and it cannot rescue {@code #start} at all, because
 * that route names no run and so there is no record to own yet. Probed: {@code ranalyst}, who holds
 * no BCM grant of any kind, transitions the run they facilitate and is 403 on {@code #start} — a
 * facilitator could drive an exercise run they were not allowed to create.
 */
class BcmAdminOnlyGuardConversionTest {

    /** The retired expression, kept as a literal so the detector below has something to fire on. */
    private static final String DEAD_PAIR = "hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')";

    /**
     * Every route this slice converted, with the guard it must now carry — exactly, not by substring.
     * The targets follow this module's own convention rather than being invented for it:
     * {@code BcmController} and {@code CrisisCommsTemplateController} both map read/write/approve/
     * delete verbs onto {@code hasPermission('BCM', …)}, and HTTP DELETE onto {@code DELETE}.
     *
     * <p>The audience diff was measured against {@code iam.role_permissions} in BOTH databases before
     * converting (the V362 landmine), and it is identical in each: {@code BCM:WRITE} is held by
     * {@code GRC_ADMINISTRATOR}, {@code PLATFORM_ADMIN} and {@code RISK_MANAGER}; {@code BCM:DELETE}
     * by the first two. {@code BCM_MANAGER} holds both, so no named role is dropped. {@code AUDITOR}
     * and {@code VIEWER} hold {@code BCM:READ}+{@code EXPORT} and neither WRITE nor DELETE, so no
     * reader-only role is admitted — probed, not reasoned.
     */
    private static final Map<String, String> CONVERTED = new LinkedHashMap<>();

    static {
        // ── the eight the census listed as pure ────────────────────────────────────────
        CONVERTED.put("BcpBinderController#archive", "hasPermission('BCM','DELETE')");
        CONVERTED.put("BiaQuantitativeController#archive", "hasPermission('BCM','DELETE')");
        CONVERTED.put("ExerciseRuntimeController#start", "hasPermission('BCM','WRITE')");
        CONVERTED.put("ExerciseScenarioLibraryController#instantiate", "hasPermission('BCM','WRITE')");
        CONVERTED.put("MtpdInheritanceController#publish", "hasPermission('BCM','WRITE')");
        CONVERTED.put("ProcessVendorDependencyController#create", "hasPermission('BCM','WRITE')");
        CONVERTED.put("ProcessVendorDependencyController#update", "hasPermission('BCM','WRITE')");
        CONVERTED.put("ProcessVendorDependencyController#delete", "hasPermission('BCM','DELETE')");

        // ── the five the COMPOSED bucket hid; F2-161's ownership leg is preserved verbatim,
        //    and note the parameter name differs by controller (#id vs #runId) ───────────
        CONVERTED.put("ExerciseRuntimeController#dispatch",
                "hasPermission('BCM','WRITE') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')");
        CONVERTED.put("ExerciseRuntimeController#customInject",
                "hasPermission('BCM','WRITE') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')");
        CONVERTED.put("ExerciseRuntimeController#transition",
                "hasPermission('BCM','WRITE') or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')");
        CONVERTED.put("ExerciseObserverController#assignObserver",
                "hasPermission('BCM','WRITE') or hasPermission(#runId, 'EXERCISE_RUN', 'OWNER')");
        CONVERTED.put("ExerciseObserverController#removeObserver",
                "hasPermission('BCM','DELETE') or hasPermission(#runId, 'EXERCISE_RUN', 'OWNER')");
    }

    /**
     * The two routes this slice deliberately did NOT convert, and the reason each must keep saying.
     *
     * <p>They are distinct approval tiers of ONE quantitative BIA — a CFO sign-off and then a board
     * sign-off — and {@code BCM:APPROVE} is one permission, so landing both on it would let a single
     * principal take both tiers. The bar that would make that safe cannot be built today:
     * {@code sys.columns} says {@code bcm.bia_quantitative} carries {@code cfo_approver_name} (a
     * caller-supplied string) and <b>no {@code board_approved_by} column at all</b>, so neither tier
     * has an identity to compare. Choosing who holds a financial versus a board approval is an owner
     * decision plus a migration; F2-152 is the prerequisite. Filed, not swept.
     *
     * <p>This map is enforced in BOTH directions on purpose. A later slice that converts one of these
     * has to delete its entry, which is the only thing that makes it read the javadoc first.
     */
    private static final Map<String, String> DELIBERATELY_STILL_ROLE_GUARDED = Map.of(
            "BiaQuantitativeController#cfoApprove", "hasAnyRole('CFO','PLATFORM_ADMIN')",
            "BiaQuantitativeController#boardApprove", "hasAnyRole('EXECUTIVE_REVIEWER','PLATFORM_ADMIN')");

    @Test
    void everyConvertedRouteAsksForABcmPermissionAndNamesNoRole() {
        TreeMap<String, String> guards = bcmGuards();
        List<String> wrong = new ArrayList<>();
        for (var e : CONVERTED.entrySet()) {
            String got = guards.get(e.getKey());
            if (got == null) {
                // A site that no longer resolves is a SILENT PASS for every other assertion here,
                // which is the failure mode F2-253 was caught by. Say so rather than skipping.
                wrong.add(e.getKey() + " — NOT FOUND (renamed or deleted; a missing subject passes)");
            } else if (!got.equals(e.getValue())) {
                wrong.add(e.getKey() + "\n      expected: " + e.getValue() + "\n      actual:   " + got);
            }
        }
        assertThat(wrong).as("""
            These thirteen routes were administrator-only because BCM_MANAGER is held by no user in \
            either database. Each must now ask for a BCM module permission, and the five run-scoped \
            ones must keep F2-161's ownership leg with the parameter name their own route declares \
            (#id on the runtime console, #runId on the observer console — a copied constant is how \
            this breaks, and SpEL answers 500 rather than 403 when it cannot resolve the name).

            Offenders: %s""".formatted(wrong)).isEmpty();
    }

    /**
     * The general form, as a hard zero over the whole module rather than over the thirteen sites.
     *
     * <p>A per-site map cannot see a fourteenth instance, and this shape has now been written at least
     * thirteen times in one package, so the next one is likelier than not. There is no exemption map:
     * the expression is only ever reachable by a platform administrator, so there is no correct code
     * for this leg to fail on (F2-214) — a route that genuinely wants oversight-plus-owner has
     * {@code hasPermission('BCM','WRITE')} available and admits strictly more people.
     */
    @Test
    void noWriteRouteInTheModuleIsDecidedByTheDeadRolePair() {
        TreeMap<String, String> writes = bcmGuards(true);
        // Anti-vacuity: a scanner that resolves nothing, or one controller, answers zero the same way
        // a clean tree does. The floor sits above the largest WRONG answer, not above zero (F2-215).
        assertThat(writes).as("the write-route scan resolved too little to mean anything").hasSizeGreaterThan(60);

        List<String> offenders = new ArrayList<>();
        writes.forEach((site, guard) -> {
            if (guard.contains(DEAD_PAIR)) offenders.add(site + " -> " + guard);
        });
        assertThat(offenders).as("""
            `%s` names one role held by nobody plus the administrator, so it admits the administrator \
            and nobody else — the same outcome as a ghost, with the difference that an admin can assign \
            the role without a jar. That is a remedy, not a defence, and it says nothing about the \
            platform as shipped.

            Offenders: %s""".formatted(DEAD_PAIR, offenders)).isEmpty();
    }

    /**
     * The detector above passes on a clean tree and also passes if the scan has rotted, so drive it
     * over a fixture carrying the literal pre-fix expression (F2-195's convention).
     */
    @Test
    void theDetectorFiresOnTheLiteralPreFixExpression() {
        String preFix = "hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN') "
                + "or hasPermission(#id, 'EXERCISE_RUN', 'OWNER')";
        assertThat(preFix.contains(DEAD_PAIR))
                .as("the composed pre-fix form must be caught, not just the bare one").isTrue();
        assertThat("hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')".contains(DEAD_PAIR))
                .as("and the bare pre-fix form").isTrue();
        assertThat("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')".contains(DEAD_PAIR))
                .as("a guard that also names a HELD role is not this defect and must NOT be flagged")
                .isFalse();
        assertThat("hasPermission('BCM','WRITE')".contains(DEAD_PAIR))
                .as("nor a converted guard").isFalse();
    }

    @Test
    void theTwoBiaApprovalTiersAreDeliberatelyStillRoleGuarded() {
        TreeMap<String, String> guards = bcmGuards();
        List<String> drift = new ArrayList<>();
        DELIBERATELY_STILL_ROLE_GUARDED.forEach((site, expected) -> {
            String got = guards.get(site);
            if (got == null) drift.add(site + " — NOT FOUND");
            else if (!got.equals(expected)) drift.add(site + " -> " + got + " (expected " + expected + ")");
        });
        assertThat(drift).as("""
            These two are NOT an oversight. They are the CFO and board tiers of one BIA, and the \
            four-eyes bar that would let them share BCM:APPROVE needs an approver identity \
            bcm.bia_quantitative does not have — there is no board_approved_by column at all. Read \
            BOARD_APPROVE_GUARD's javadoc before changing either.

            Drift: %s""".formatted(drift)).isEmpty();
    }

    /**
     * The reverse direction, which is the one nobody writes first (F2-199): a pin must still name a
     * route the scan finds, so this cannot quietly become a statement about nothing. It also refuses
     * the overlap — a site cannot be both converted and pinned.
     */
    @Test
    void thePinAndTheConversionMapAreDisjointAndBothStillNameLiveRoutes() {
        TreeMap<String, String> guards = bcmGuards();
        assertThat(guards.keySet()).as("every pinned site must still exist")
                .containsAll(DELIBERATELY_STILL_ROLE_GUARDED.keySet());
        assertThat(guards.keySet()).as("every converted site must still exist")
                .containsAll(CONVERTED.keySet());
        assertThat(CONVERTED.keySet()).as("a route cannot be both converted and deliberately pinned")
                .doesNotContainAnyElementsOf(DELIBERATELY_STILL_ROLE_GUARDED.keySet());
        assertThat(CONVERTED).as("the thirteen routes this slice converted").hasSize(13);
    }

    /**
     * The READ guards keep their role lists, and that is a measurement rather than an omission:
     * {@code CISO} is named on them and holds only {@code BCM:EXPORT} — no {@code BCM:READ} — so
     * converting a read guard to {@code hasPermission('BCM','READ')} would DROP a live role.
     * {@code so_officer} is the CISO here and reads these registers today.
     *
     * <p>🔑 F2-253 found the same asymmetry on {@code SubprocessorController} and filed the read half
     * as an owner decision for exactly this reason: correcting a ghost widens access, and converting a
     * read leg can narrow it. This leg exists so "finish the sweep" fails the build.
     */
    @Test
    void theReadGuardsKeepTheirRoleListsBecauseConvertingThemWouldDropALiveRole() {
        TreeMap<String, String> guards = bcmGuards();
        List<String> mustStillNameCiso = List.of(
                "BcpBinderController#list",
                "ExerciseScenarioLibraryController#list",
                "MtpdInheritanceController#list",
                "ProcessVendorDependencyController#list");
        List<String> lost = new ArrayList<>();
        for (String site : mustStillNameCiso) {
            String g = guards.get(site);
            if (g == null) lost.add(site + " — NOT FOUND");
            else if (!g.contains("CISO")) lost.add(site + " -> " + g);
        }
        assertThat(lost).as("""
            CISO holds BCM:EXPORT and NOT BCM:READ, so converting these read guards would drop a role \
            that reads these registers today. Measured in dev and prod. If you are changing one, \
            change the grant first and say so.

            Offenders: %s""".formatted(lost)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────

    private static TreeMap<String, String> bcmGuards() {
        return bcmGuards(false);
    }

    /** @param writesOnly keep only methods mapped to a mutating HTTP verb. */
    private static TreeMap<String, String> bcmGuards(boolean writesOnly) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        TreeMap<String, String> out = new TreeMap<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module.bcm")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {                                              // NOPMD
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) continue;
                if (writesOnly && !isWrite(m)) continue;
                out.put(c.getSimpleName() + "#" + m.getName(), pa.value());
            }
        }
        return out;
    }

    private static boolean isWrite(Method m) {
        return m.getAnnotation(PostMapping.class) != null
                || m.getAnnotation(PutMapping.class) != null
                || m.getAnnotation(DeleteMapping.class) != null
                || m.getAnnotation(PatchMapping.class) != null;
    }
}
