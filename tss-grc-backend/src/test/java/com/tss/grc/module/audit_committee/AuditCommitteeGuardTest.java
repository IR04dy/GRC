package com.tss.grc.module.audit_committee;

import com.tss.grc.module.audit_committee.dashboard.controller.AuditCommitteeDashboardController;
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

    /** Endpoint -> the audience it must have. Exhaustive: an unlisted endpoint fails. */
    private static final Map<String, Set<String>> EXPECTED = new LinkedHashMap<>() {{
        put("live",             READ_TIER);
        put("list",             READ_TIER);
        put("get",              READ_TIER);
        put("generate",         WRITE_TIER);
        put("recordResolution", WRITE_TIER);
        put("finalizeSnapshot", WRITE_TIER);
        put("attest",           ATTEST_TIER);
    }};

    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

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

    private static Set<String> namesIn(String guard) {
        Set<String> out = new TreeSet<>();
        Matcher m = QUOTED.matcher(guard);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
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
        assertThat(checked).as("no role names parsed out of any guard — the scan collapsed").isGreaterThan(10);
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
