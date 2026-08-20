package com.tss.grc.module.audit_committee;

import com.tss.grc.module.audit_committee.dashboard.controller.AuditCommitteeDashboardController;
import com.tss.grc.support.GuardExpressions;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-52 — the audit committee could not generate, resolve, finalise or attest its own dashboard.
 *
 * <p>Three guards, measured against {@code iam.roles} in both databases:
 *
 * <pre>
 *   READ    10 names, 4 ghosts  -> 6 real roles read it; the ghosts narrowed nothing
 *   WRITE    3 names, 2 ghosts  -> PLATFORM_ADMIN ALONE, while the comment said
 *                                  "the Chair + Secretary + Platform Admin trio"
 *   ATTEST   6 names, 3 ghosts  -> the two auditors + the admin, while the comment said
 *                                  "every voting Committee member + the Chair"
 * </pre>
 *
 * <p>So the snapshot SAMA CG §6 and CMA CG Article 54 require the committee to report from could be
 * produced by nobody but the platform administrator, and an audit-committee attestation could be
 * signed by the auditors being assured and by no committee member at all.
 *
 * <p>⚠⚠ <b>F2-259 — the WRITE row above was corrected and stayed administrator-only, and this class
 * could not see that.</b> Replacing the ghost {@code COMPANY_SECRETARY} with the real
 * {@code BOARD_SECRETARY} left a tier whose only non-administrator name is <b>held by no user in
 * either database</b>. 🔑 This class asks whether a recited name <em>exists</em>; the question
 * {@code hasAnyRole} puts is whether anybody <em>holds</em> it, and only the second one decides a
 * request. The three snapshot-mutating routes are now {@code hasPermission('AUDIT','WRITE')} — the
 * permission all four peer controllers under this same SKU already use — with V382 granting the
 * retired role that pair so the conversion drops nobody. The read and attest tiers are deliberately
 * unchanged; {@code AuditCommitteeGuardConversionTest} records why for all three.
 *
 * <p>🔑 {@code COMPANY_SECRETARY} was a <b>near-miss</b>: {@code BOARD_SECRETARY} exists and is that
 * person. The Chair has no equivalent — no role expresses committee membership and F2-26's
 * {@code committee_member_terms.member_user_id} is NULL on all five audit-committee seats — so the
 * Chair leg is <b>not</b> restored here. An entity-bound leg that can never be true is a control that
 * cannot fire (F2-112). F2-151 carries that, and the self-declared {@code attestedRole} beside it.
 */
class AuditCommitteeGuardTest {

    /** Read 2026-08-06 from both databases; only has to answer "does this name exist". */
    private static final Set<String> REAL_ROLES = Set.of(
        "PLATFORM_ADMIN", "CISO", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR",
        "EXTERNAL_AUDITOR", "BOARD_SECRETARY", "AUDITOR", "EXECUTIVE_REVIEWER", "ORG_ADMIN",
        "GRC_ADMINISTRATOR");

    /** The names this slice removed. None has a role, a roster link, or a substitute. */
    private static final Set<String> REMOVED_GHOSTS = Set.of(
        "AUDIT_COMMITTEE_CHAIR", "AUDIT_COMMITTEE_MEMBER", "BOARD_MEMBER", "COMPANY_SECRETARY");

    private static final Set<String> READ_TIER = new TreeSet<>(Set.of(
        "CISO", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR", "EXTERNAL_AUDITOR",
        "BOARD_SECRETARY", "PLATFORM_ADMIN"));
    private static final Set<String> WRITE_TIER = new TreeSet<>(Set.of(
        "BOARD_SECRETARY", "PLATFORM_ADMIN"));
    private static final Set<String> ATTEST_TIER = new TreeSet<>(Set.of(
        "EXTERNAL_AUDITOR", "INTERNAL_AUDITOR", "BOARD_SECRETARY", "PLATFORM_ADMIN"));

    /**
     * Endpoint -> the ROLE audience it must have. Exhaustive: an unlisted endpoint fails.
     *
     * <p>⚠ F2-259 — the three snapshot-mutating routes recite no role at all now; they are guarded
     * by {@code hasPermission('AUDIT','WRITE')}. An empty expectation on its own would also be
     * satisfied by a route with <b>no guard</b>, so {@link #PERMISSION_GUARDED} pins what replaced
     * the names and both are asserted. {@link #WRITE_TIER} is kept as the historical record of what
     * that audience used to be.
     */
    private static final Map<String, Set<String>> EXPECTED = new LinkedHashMap<>() {{
        put("live",             READ_TIER);
        put("list",             READ_TIER);
        put("get",              READ_TIER);
        put("generate",         Set.of());
        put("recordResolution", Set.of());
        put("finalizeSnapshot", Set.of());
        put("attest",           ATTEST_TIER);
    }};

    /**
     * F2-259 — the routes whose audience is a permission rather than a role list, and the exact
     * expression each must carry. {@code BOARD_SECRETARY} was the only non-administrator name on
     * {@link #WRITE_TIER} and <b>is held by no user in either database</b>, so that tier admitted the
     * platform administrator alone — which is what the class javadoc above says F2-52 was fixing.
     * F2-52 asked whether the name existed; the question {@code hasAnyRole} puts is whether anybody
     * holds it. V382 grants the retired role {@code AUDIT:WRITE} so the conversion drops nobody.
     */
    private static final Map<String, String> PERMISSION_GUARDED = new LinkedHashMap<>() {{
        put("generate",         "hasPermission('AUDIT','WRITE')");
        put("recordResolution", "hasPermission('AUDIT','WRITE')");
        put("finalizeSnapshot", "hasPermission('AUDIT','WRITE')");
    }};

    /**
     * ⚠⚠ <b>F2-259 — this used to be {@code '([^']*)'} run over the WHOLE expression, so
     * {@code hasPermission('AUDIT','WRITE')} parsed as two role names, {@code AUDIT} and
     * {@code WRITE}, and both legs below failed on a CORRECT role-to-permission conversion.</b>
     *
     * <p>That is the one failure a gate must never have (F2-214): it trains the next reader to edit
     * the assertion rather than the code. It is also <b>the identical defect F2-254 repaired in
     * {@code BiaApprovalChainGuardTest}</b> — same author slice (F2-52), same purpose, and this
     * sibling kept the bug, so 🔑 a ghost-role detector written as a bare quoted-token scan flags
     * every conversion F2-05 exists to make. Now scoped to the argument list of
     * {@code hasAnyRole(…)} / {@code hasRole(…)}, which is the shape the claim is about: this class
     * asks whether a recited ROLE NAME exists, and a permission pair is not a role name.
     *
     * <p>⚠ <b>F2-262 — the pattern moved out of this class rather than being deleted.</b> This
     * repair was the third of five, one per converting slice, and the fifth arrived before anybody
     * extracted the primitive: measured, <b>eight</b> test classes hand-rolled this question in
     * <b>three</b> mechanisms and <b>six</b> spellings. It now lives once, in
     * {@link GuardExpressions#rolesRecitedBy}. The positive control below is unchanged and is now
     * more valuable, because it guards the shared answer rather than this copy of it.
     */
    private static Set<String> namesIn(String guard) {
        return GuardExpressions.rolesRecitedBy(guard);
    }

    private static Map<String, String> guards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : AuditCommitteeDashboardController.class.getDeclaredMethods()) {
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            if (pa != null) {
                found.put(m.getName(), pa.value());
            }
        }
        assertThat(found).as(
            "reflection found %d guarded methods; %d endpoints are pinned below. An endpoint that is "
            + "not listed is an unreviewed guard, and a scan that finds none asserts nothing.",
            found.size(), EXPECTED.size())
            .hasSize(EXPECTED.size());
        return found;
    }

    @Test
    @DisplayName("every endpoint's audience is the reviewed one")
    void everyEndpointHasItsReviewedAudience() {
        Map<String, String> actual = guards();
        // Named-key lookup, asserted rather than dereferenced: a method renamed out from under this
        // map used to NullPointer inside namesIn, which reports a harness bug rather than the
        // mis-tiering it is. Fail with the name.
        assertThat(actual.keySet()).as(
            "these endpoints are pinned but no longer exist on the controller (renamed or removed), "
            + "so their guards are unreviewed")
            .containsAll(EXPECTED.keySet());

        Map<String, Set<String>> mismatch = new TreeMap<>();
        for (var e : EXPECTED.entrySet()) {
            Set<String> named = namesIn(actual.get(e.getKey()));
            if (!named.equals(new TreeSet<>(e.getValue()))) {
                mismatch.put(e.getKey(), named);
            }
        }
        assertThat(mismatch).as("""
            An audit-committee endpoint's audience is not the one this slice reviewed. Widening lets \
            someone outside the assurance chain produce or sign the committee's reporting artefact; \
            narrowing leaves the committee unable to report at all, which is how this started.""")
            .isEmpty();

        // F2-259: an empty role audience is also what an UNGUARDED route yields, so the three
        // permission-guarded routes are pinned by expression as well.
        Map<String, String> permMismatch = new TreeMap<>();
        for (var e : PERMISSION_GUARDED.entrySet()) {
            if (!e.getValue().equals(actual.get(e.getKey()))) {
                permMismatch.put(e.getKey(), String.valueOf(actual.get(e.getKey())));
            }
        }
        assertThat(permMismatch).as("""
            F2-259: these routes must be guarded by the permission, not by a role list and not by \
            nothing. BOARD_SECRETARY exists in iam.roles and is held by nobody, so the role guard \
            they used to carry admitted the platform administrator alone.""")
            .isEmpty();
    }

    /**
     * F2-259 — the positive control for the role-extractor repair, carrying the literal pre-fix
     * and post-fix guard shapes.
     *
     * <p>F2-254's rule for a detector repair is that <b>the repaired detector must still answer the
     * same on the tree the original passed on</b>. Arguing that is not the same as checking it, so
     * this asserts it: the two pure role guards that survive this slice parse to exactly the role
     * sets the old whole-expression scan produced, and the permission guard that replaced the third
     * parses to <b>nothing</b> — where the old scan produced {@code [AUDIT, WRITE]} and failed the
     * build on correct code.
     */
    @Test
    @DisplayName("the role-name extractor reads role calls only — pre-fix and post-fix shapes")
    void theRoleNameExtractorIsScopedToRoleCalls() {
        String preFixWrite = "hasAnyRole('BOARD_SECRETARY','PLATFORM_ADMIN')";
        String postFixWrite = "hasPermission('AUDIT','WRITE')";
        String preFixRead = "hasAnyRole('CISO','RISK_MANAGER','COMPLIANCE_OFFICER',"
                + "'INTERNAL_AUDITOR','EXTERNAL_AUDITOR','BOARD_SECRETARY','PLATFORM_ADMIN')";

        assertThat(namesIn(preFixWrite))
            .as("a pure role guard must parse exactly as it always did")
            .containsExactlyInAnyOrderElementsOf(WRITE_TIER);
        assertThat(namesIn(preFixRead))
            .as("the read tier is untouched by this slice and must parse identically")
            .containsExactlyInAnyOrderElementsOf(READ_TIER);
        assertThat(namesIn(postFixWrite))
            .as("F2-259: a permission pair is NOT a pair of role names. The old scan returned "
                + "[AUDIT, WRITE] here and failed the build on a correct conversion — F2-254's "
                + "finding, in a sibling gate written by the same slice.")
            .isEmpty();
        assertThat(namesIn("hasPermission('AUDIT','WRITE') or hasAnyRole('BOARD_SECRETARY')"))
            .as("a COMPOSED guard must still yield its role leg — scoping the scan must not blind "
                + "it to the shape F2-159/F2-161 controls are built from")
            .containsExactly("BOARD_SECRETARY");
        assertThat(namesIn("hasPermission(#id,'EXERCISE_RUN','OWNER')"))
            .as("a three-argument ownership guard names no role either")
            .isEmpty();
    }

    @Test
    @DisplayName("no guard here names a role that does not exist")
    void noGuardNamesANonexistentRole() {
        Map<String, List<String>> offenders = new TreeMap<>();
        int checked = 0;
        for (var e : guards().entrySet()) {
            List<String> bad = namesIn(e.getValue()).stream()
                .filter(n -> !REAL_ROLES.contains(n)).sorted().toList();
            checked += namesIn(e.getValue()).size();
            if (!bad.isEmpty()) {
                offenders.put(e.getKey(), bad);
            }
        }
        // Anti-vacuity. Measured 11 after F2-259 (READ 7 + ATTEST 4; the write tier recites none),
        // down from 13. A floor has to sit above the largest WRONG answer, and the wrong answer here
        // is an extractor that stops matching and returns nothing at all (F2-215).
        assertThat(checked).as("no role names parsed out of any guard — the scan collapsed")
            .isGreaterThanOrEqualTo(11);
        assertThat(offenders).as("""
            %s names a role absent from iam.roles. hasAnyRole('X') tests for the authority ROLE_X, so \
            this is a permanently false OR leg: no compile error, no startup warning, no log line. It \
            is how the write guard came to admit the platform administrator alone while its comment \
            named a trio.""".formatted(offenders)).isEmpty();
    }

    @Test
    @DisplayName("the removed ghosts do not return, on any tier")
    void theGhostsStayGone() {
        for (var e : guards().entrySet()) {
            Set<String> named = namesIn(e.getValue());
            List<String> back = REMOVED_GHOSTS.stream().filter(named::contains).sorted().toList();
            assertThat(back).as("""
                %s is back on %s. If a role by that name now exists in iam.roles, add it to \
                REAL_ROLES and to the tier deliberately — and if it is AUDIT_COMMITTEE_CHAIR or \
                AUDIT_COMMITTEE_MEMBER, check F2-151 first: committee membership is carried by \
                admin.committee_member_terms, and a role would say "some chair" where the control \
                needs "the chair of this committee".""".formatted(back, e.getKey()))
                .isEmpty();
        }
    }

    @Test
    @DisplayName("write is strictly narrower than read, and attest sits between them")
    void theTiersStayOrdered() {
        // The property, not the lists: a snapshot's producer must be a subset of its readers, or
        // someone can write a board-reporting artefact they cannot themselves see.
        assertThat(READ_TIER).as("every writer must also be a reader").containsAll(WRITE_TIER);
        assertThat(READ_TIER).as("every attester must also be a reader").containsAll(ATTEST_TIER);
        assertThat(WRITE_TIER.size()).as("write must stay narrower than read").isLessThan(READ_TIER.size());
    }
}
