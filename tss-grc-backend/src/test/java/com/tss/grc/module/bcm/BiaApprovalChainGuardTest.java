package com.tss.grc.module.bcm;

import com.tss.grc.module.bcm.controller.BiaQuantitativeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-52 — the board sign-off on a quantitative BIA was the platform administrator's alone.
 *
 * <p>{@code board-approve} guarded with {@code hasAnyRole('BOARD_MEMBER','PLATFORM_ADMIN')}, and
 * {@code BOARD_MEMBER} is a row in {@code iam.roles} in neither database. It was the platform's
 * <b>last remaining attrition lockout</b> — the census's "PLATFORM_ADMIN alone, by attrition" count
 * went 8 → 4 → 1 → 0 across four slices, and this was the one.
 *
 * <p>Measured live before the fix: dev holds one quantitative BIA and it sits at
 * {@code CFO_APPROVED} — <b>parked at exactly this stage</b>, waiting for a board approval only the
 * platform administrator could give, on the step whose payload is a board-minutes reference.
 *
 * <h3>🔑 Why {@code BCM:APPROVE} was the wrong target</h3>
 * The obvious conversion resolves to {BCM_MANAGER, EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, ORG_ADMIN,
 * PLATFORM_ADMIN} — and {@code BCM_MANAGER} is stage <b>one</b> of this same chain
 * ({@code bcm-review}). It would let one person perform the lead review and then the board sign-off
 * on their own submission. {@code guardTransition} enforces the <em>order</em> of the stages; nothing
 * enforces that they are different people, so these guards are the only separation there is. That is
 * the property this class pins, and it is why measuring a permission's holders has to come before
 * calling a conversion a conversion.
 *
 * <p>⚠ Empirically established while probing this, because the framework's rule is easy to get
 * backwards: a method-level {@code @PreAuthorize} <b>replaces</b> the class-level one rather than
 * ANDing with it. {@code EXECUTIVE_REVIEWER} is absent from the class-level list and still gets 200
 * on {@code board-approve}. The class annotation is therefore the guard for exactly the two methods
 * that have none of their own.
 */
class BiaApprovalChainGuardTest {

    private static final Set<String> REAL_ROLES = Set.of(
        "PLATFORM_ADMIN", "BCM_MANAGER", "CISO", "RISK_MANAGER", "AUDITOR", "CFO",
        "EXECUTIVE_REVIEWER", "GRC_ADMINISTRATOR", "ORG_ADMIN", "BCM_LEAD", "BIA_OWNER");

    /** Stage -> the roles that may perform it. The chain, as reviewed. */
    private static final Map<String, Set<String>> STAGES = new LinkedHashMap<>() {{
        put("submitForReview", new TreeSet<>(Set.of("BCM_MANAGER", "CISO", "PLATFORM_ADMIN")));
        put("bcmLeadReview",   new TreeSet<>(Set.of("BCM_MANAGER", "CISO", "PLATFORM_ADMIN")));
        put("cfoApprove",      new TreeSet<>(Set.of("CFO", "PLATFORM_ADMIN")));
        put("boardApprove",    new TreeSet<>(Set.of("EXECUTIVE_REVIEWER", "PLATFORM_ADMIN")));
    }};

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    private static Map<String, String> methodGuards() {
        Map<String, String> out = new TreeMap<>();
        for (Method m : BiaQuantitativeController.class.getDeclaredMethods()) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            if (pa != null) {
                out.put(m.getName(), pa.value());
            }
        }
        assertThat(out.keySet()).as(
            "these approval stages have no method-level @PreAuthorize, so they fall back to the "
            + "class-level READ list and are not guarded as approvals at all")
            .containsAll(STAGES.keySet());
        return out;
    }

    private static Set<String> namesIn(String guard) {
        Set<String> out = new TreeSet<>();
        Matcher m = QUOTED.matcher(guard);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    @DisplayName("every stage of the approval chain has its reviewed audience")
    void everyStageHasItsReviewedAudience() {
        Map<String, String> guards = methodGuards();
        Map<String, Set<String>> drift = new TreeMap<>();
        for (var e : STAGES.entrySet()) {
            Set<String> named = namesIn(guards.get(e.getKey()));
            if (!named.equals(e.getValue())) {
                drift.put(e.getKey(), named);
            }
        }
        assertThat(drift).as("""
            An approval stage's audience is not the one this slice reviewed. On this controller a \
            widening is not a convenience: the three stages are an escalation, and the guards are \
            what keep them separate people.""").isEmpty();
    }

    @Test
    @DisplayName("no stage names a role that does not exist")
    void noStageNamesANonexistentRole() {
        Map<String, List<String>> bad = new TreeMap<>();
        int names = 0;
        for (var e : methodGuards().entrySet()) {
            Set<String> named = namesIn(e.getValue());
            names += named.size();
            List<String> ghosts = named.stream().filter(n -> !REAL_ROLES.contains(n)).sorted().toList();
            if (!ghosts.isEmpty()) {
                bad.put(e.getKey(), ghosts);
            }
        }
        assertThat(names).as("no role names parsed — the scan collapsed").isGreaterThan(10);
        assertThat(bad).as("""
            %s names a role absent from iam.roles. That is a permanently false OR leg — it is how \
            board-approve came to admit the platform administrator alone, on the step that records a \
            board-minutes reference.""".formatted(bad)).isEmpty();
    }

    /**
     * The property, not the lists: an escalation whose stages share a performer is not an escalation.
     *
     * <p>PLATFORM_ADMIN is excluded because it is deliberately on every stage — a break-glass path
     * that is a stated decision, not an accident of attrition. The assertion is about the domain
     * roles.
     */
    @Test
    @DisplayName("no domain role can perform both the lead review and the board sign-off")
    void theChainStaysSeparable() {
        Set<String> lead = new TreeSet<>(STAGES.get("bcmLeadReview"));
        Set<String> board = new TreeSet<>(STAGES.get("boardApprove"));
        Set<String> cfo = new TreeSet<>(STAGES.get("cfoApprove"));
        lead.remove("PLATFORM_ADMIN");
        board.remove("PLATFORM_ADMIN");
        cfo.remove("PLATFORM_ADMIN");

        assertThat(lead).as("anti-vacuity: the lead tier must have a domain role").isNotEmpty();
        assertThat(board).as("anti-vacuity: the board tier must have a domain role").isNotEmpty();

        assertThat(board).as("""
            %s can perform both the BCM lead review and the board sign-off, so one person can carry a \
            quantitative BIA from draft to board-approved. This is exactly why BCM:APPROVE was NOT \
            used here: it includes BCM_MANAGER, the stage-one role.""".formatted(board))
            .doesNotContainAnyElementsOf(lead);
        assertThat(board).as("the board tier must not also be the CFO tier")
            .doesNotContainAnyElementsOf(cfo);
    }

    @Test
    @DisplayName("board-approve is not converted to a module permission without re-deciding this")
    void boardApproveIsNotSilentlyConvertedToBcmApprove() {
        String guard = methodGuards().get("boardApprove");
        assertThat(guard).as("""
            board-approve must not become hasPermission('BCM','APPROVE'). Measured in both databases, \
            BCM:APPROVE is held by BCM_MANAGER, EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, ORG_ADMIN and \
            PLATFORM_ADMIN — and BCM_MANAGER is stage one of this chain. If a genuinely board-tier \
            permission is ever catalogued, change this test, the controller javadoc and the SoD \
            assertion above together, and say who holds it.""")
            .doesNotContain("hasPermission");
    }
}
