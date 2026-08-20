package com.tss.grc.module.bcm.cycle;

import com.tss.grc.module.bcm.cycle.controller.CrisisEventController;
import com.tss.grc.module.bcm.cycle.controller.MobileCrisisController;
import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-52 — the crisis room excluded the crisis manager and named an invented role instead.
 *
 * <p>Both {@code active-summary} endpoints guarded with
 * {@code hasAnyRole('CRISIS_COMMANDER','CISO','BCM_MANAGER','PLATFORM_ADMIN','INCIDENT_COMMANDER')}.
 * Neither {@code CRISIS_COMMANDER} nor {@code INCIDENT_COMMANDER} exists in {@code iam.roles} in
 * either database, so both legs were permanently false — while the platform ships
 * {@code CRISIS_MANAGER} ("Crisis Manager", holder of {@code BCM:READ}), which the guard did not
 * name. A ghost role beside a near-miss of a real one.
 *
 * <p>{@code INCIDENT_COMMANDER} was dropped rather than substituted. The nearest existing name,
 * {@code EMERGENCY_RESPONDER}, is <i>"Emergency Responder (Break-glass)"</i>; putting the IAM
 * break-glass role on a routine command-loop read would conflate an emergency override with daily
 * access. <b>Dropping a leg that never matched is behaviour-preserving; inventing an equivalence is
 * not</b> — and this test exists partly to stop a later sweep making that substitution.
 */
class CrisisRoomGuardTest {

    /**
     * Roles that exist in {@code iam.roles}, read 2026-08-06 from both databases. A copy, and copies
     * drift — but the alternative is a database in a suite that must not need one, and
     * {@code RoleGuardConversionRatchetTest} owns the platform-wide version of this check. Here it
     * only has to answer: does every name in the crisis-room guard exist?
     */
    private static final Set<String> REAL_ROLES = Set.of(
        "PLATFORM_ADMIN", "CISO", "BCM_MANAGER", "CRISIS_MANAGER", "EMERGENCY_RESPONDER",
        "DR_OWNER", "BIA_OWNER", "SERVICE_OWNER", "RISK_MANAGER", "GRC_ADMINISTRATOR", "ORG_ADMIN");

    /** Roles the crisis-room guard must name — exactly, no more and no fewer. */
    private static final Set<String> EXPECTED_AUDIENCE = new TreeSet<>(Set.of(
        "CRISIS_MANAGER", "CISO", "BCM_MANAGER", "PLATFORM_ADMIN"));

    /** The two names this slice removed. Neither may come back without a role to back it. */
    private static final Set<String> REMOVED_GHOSTS = Set.of(
        "CRISIS_COMMANDER", "INCIDENT_COMMANDER");

    private static String guardOf(Class<?> controller, String method) {
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                assertThat(pa).as("%s#%s has no @PreAuthorize at all — the crisis room is open",
                    controller.getSimpleName(), method).isNotNull();
                return pa.value();
            }
        }
        throw new AssertionError(controller.getSimpleName() + "#" + method + " no longer exists; "
            + "this test cannot assert anything about a method that is gone");
    }

    /**
     * The ROLE names in a guard — not every quoted token in it.
     *
     * <p>⚠⚠ <b>F2-262: this ran a bare {@code '([^']*)'} over the whole expression, with no notion
     * of which function a token was an argument to.</b> It was the last of eight hand-rolled
     * extractors and the only one still completely unscoped, so the moment
     * {@link #theGuardIsNotQuietlyReplacedByTheModuleRead} were ever answered in the affirmative —
     * i.e. the moment somebody converted this guard — {@code hasPermission('BCM','READ')} would
     * have yielded two "role names" called {@code BCM} and {@code READ}, and
     * {@link #theAudienceIsTheReviewedOne} would have reported <b>both as ghost roles</b>. That is
     * a gate failing on correct code (F2-214), and it is the same defect F2-254, F2-259 and F2-260
     * each repaired in their own module's copy.
     *
     * <p>🔑 It was latent only because {@code CRISIS_ROOM_GUARD} happens to be a pure role guard,
     * while every other guard on the same controller is {@code hasPermission('BCM', …)} — so the
     * detonator is the conversion this class's fourth test is written to adjudicate. Reachability is
     * a fact about today, not a property of a control (F2-182).
     *
     * <p>Now {@link GuardExpressions#rolesRecitedBy}, the platform's single answer to this question.
     */
    private static Set<String> namesIn(String guard) {
        return GuardExpressions.rolesRecitedBy(guard);
    }

    @Test
    @DisplayName("the crisis room names the real crisis-manager role and nothing invented")
    void theAudienceIsTheReviewedOne() {
        Set<String> named = namesIn(CrisisEventController.CRISIS_ROOM_GUARD);

        assertThat(named).as("""
            The crisis-room audience is not the one this slice reviewed. Adding a name here widens \
            who can see the live command loop; removing one locks a commander out mid-incident. \
            Either way it is a decision, not a tidy-up.""")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_AUDIENCE);

        List<String> nonexistent = named.stream().filter(n -> !REAL_ROLES.contains(n)).sorted().toList();
        assertThat(nonexistent).as("""
            %s is named by the crisis-room guard and is not a role. hasAnyRole('X') tests for the \
            authority ROLE_X: a name that matches nothing is a permanently false OR leg, with no \
            compile error, no startup warning and no log line. That is how the crisis manager came \
            to be excluded from the crisis room.""".formatted(nonexistent))
            .isEmpty();
    }

    @Test
    @DisplayName("neither dropped ghost returns, and EMERGENCY_RESPONDER is not smuggled in as one")
    void theGhostsStayGone() {
        Set<String> named = namesIn(CrisisEventController.CRISIS_ROOM_GUARD);

        List<String> returned = new ArrayList<>(REMOVED_GHOSTS);
        returned.retainAll(named);
        assertThat(returned).as("""
            %s is back on the crisis-room guard. If a role by that name now genuinely exists in \
            iam.roles, add it to REAL_ROLES and EXPECTED_AUDIENCE deliberately. If it does not, this \
            is the defect returning: a leg that can never be true, making the guard read wider than \
            it behaves.""".formatted(returned)).isEmpty();

        assertThat(named).as("""
            EMERGENCY_RESPONDER is "Emergency Responder (Break-glass)" — the IAM break-glass role, \
            not an incident commander. It is the obvious substitute for the dropped \
            INCIDENT_COMMANDER leg and it is the wrong one: break-glass on a routine command-loop \
            read conflates an emergency override with daily access.""")
            .doesNotContain("EMERGENCY_RESPONDER");
    }

    @Test
    @DisplayName("the mobile façade carries the identical guard, by reference")
    void theFacadeCannotDriftFromItsTwin() {
        String canonical = guardOf(CrisisEventController.class, "activeSummary");
        String facade = guardOf(MobileCrisisController.class, "activeSummary");

        assertThat(facade).as("""
            MobileCrisisController#activeSummary serves the same service method under the mobile URL \
            family. A different guard means one of the two URLs is the wrong answer — and the mobile \
            copy is the one nobody re-reads. Reference CrisisEventController.CRISIS_ROOM_GUARD \
            rather than restating the string.""")
            .isEqualTo(canonical);

        // Anti-vacuity: two identical empty strings would satisfy the equality above.
        assertThat(canonical).isEqualTo(CrisisEventController.CRISIS_ROOM_GUARD);
        assertThat(namesIn(canonical)).hasSize(EXPECTED_AUDIENCE.size());
    }

    @Test
    @DisplayName("the summary stays narrower than BCM:READ, which is 17 roles including VIEWER")
    void theGuardIsNotQuietlyReplacedByTheModuleRead() {
        String guard = CrisisEventController.CRISIS_ROOM_GUARD;
        assertThat(guard).as("""
            The crisis-room summary must not become hasPermission('BCM','READ'). Measured in both \
            databases: BCM:READ is held by 17 roles including VIEWER and REGULATOR_VIEWER, so that \
            conversion takes this endpoint from 4 named roles to 17 — on the one surface whose own \
            comment says it is the live command loop rather than the wider read view. BCM:APPROVE is \
            not the answer either: 5 roles, and it excludes CISO.

            If a commander-tier permission is ever catalogued, change this test and the controller \
            javadoc together, and say who holds it.""")
            .doesNotContain("hasPermission");
    }
}
