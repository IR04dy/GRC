package com.tss.grc.module.audit_lc.ia_plan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-05 — the internal-audit plan surface authorises on the {@code AUDIT} permission, not on role
 * names that may or may not exist.
 *
 * <h3>What was wrong</h3>
 * <p>All 26 endpoints guarded with {@code hasAnyRole}. The read guards recited twelve roles, the
 * write guards up to five, and <b>five of the twelve are absent from {@code iam.roles}</b>:
 * CHIEF_AUDIT_EXECUTIVE, AUDIT_DIRECTOR, INTERNAL_AUDITOR_LEAD, AUDIT_COMMITTEE_MEMBER,
 * BOARD_MEMBER. Because {@code hasAnyRole} never reaches {@code ModulePermissionEvaluator}, it also
 * never reaches the hardcoded PLATFORM_ADMIN bypass — the population that passed was exactly the
 * surviving real names, and on <b>11 of the 26</b> that was PLATFORM_ADMIN alone: create the annual
 * plan, create/update/delete the universe, bulk import, recompute residual, transition a plan or an
 * engagement, delete a plan or engagement.
 *
 * <p>Live before the change, {@code auditor} (AUDITOR + INTERNAL_AUDITOR) got <b>403</b> on
 * {@code POST /api/v1/ia/plan} — an internal auditor could not plan the audit year, and the role
 * that would have let them cannot be created.
 *
 * <h3>Why this test rather than a Spring Security slice</h3>
 * <p>The defect was the <em>content of an annotation</em>, not the behaviour of a filter chain: the
 * expression evaluated exactly as written, and what was wrong was what it named. So the regression
 * to prevent is textual — a guard drifting back to a role list, or a new endpoint arriving with
 * one — and that is what is asserted here, per-method and by inventory. The population behind each
 * permission is a database fact, checked live and recorded in
 * {@code Appendix/ROLE_GUARD_CENSUS.md}; {@link com.tss.grc.security.RoleGuardConversionRatchetTest}
 * keeps the platform-wide count from growing back.
 *
 * <p>Same shape as {@code WorkflowRecipientListGuardTest} (F2-51) and {@code AiSuggestionGuardTest}
 * (F2-50).
 */
class IaControllerGuardTest {

    private static final String READ = "hasPermission('AUDIT','READ')";
    private static final String WRITE = "hasPermission('AUDIT','WRITE')";
    private static final String DELETE = "hasPermission('AUDIT','DELETE')";

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // ── reads: the plan, the universe and the engagement register ──────────────────────────
        EXPECTED.put("listPlans", READ);
        EXPECTED.put("getPlan", READ);
        EXPECTED.put("planDashboard", READ);
        EXPECTED.put("listAllUniverse", READ);
        EXPECTED.put("listMasterUniverse", READ);
        EXPECTED.put("listUniverseForPlan", READ);
        EXPECTED.put("getUniverse", READ);
        EXPECTED.put("listAllEngagements", READ);
        EXPECTED.put("listEngagementsForPlan", READ);
        EXPECTED.put("getEngagement", READ);

        // ── writes. transitionPlan is WRITE, not APPROVE, because AuditLifecycleController's own
        //    annual-plan transition is WRITE and AUDIT:APPROVE is not in the catalogue — guarding
        //    on an uncatalogued permission is F2-23, a guard that can never pass.
        EXPECTED.put("createPlan", WRITE);
        EXPECTED.put("updatePlan", WRITE);
        EXPECTED.put("transitionPlan", WRITE);
        EXPECTED.put("createUniverse", WRITE);
        EXPECTED.put("updateUniverse", WRITE);
        EXPECTED.put("bulkImport", WRITE);
        EXPECTED.put("recomputeResidual", WRITE);
        EXPECTED.put("createEngagement", WRITE);
        EXPECTED.put("createFromUniverse", WRITE);
        EXPECTED.put("updateEngagement", WRITE);
        EXPECTED.put("transitionEngagement", WRITE);
        EXPECTED.put("createMilestone", WRITE);
        EXPECTED.put("recordMilestone", WRITE);

        // ── deletes: soft, but they retire a register the audit committee reports from ─────────
        EXPECTED.put("deletePlan", DELETE);
        EXPECTED.put("deleteUniverse", DELETE);
        EXPECTED.put("deleteEngagement", DELETE);
    }

    private static Map<String, String> actualGuards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : IaController.class.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
            if (!mapped) continue;
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    @Test
    void everyEndpointCarriesTheExpectedAuditPermission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((method, guard) ->
            assertThat(actual)
                .as("%s must be guarded by %s", method, guard)
                .containsEntry(method, guard));
    }

    /**
     * The regression that matters: not "is there a guard" but "does it name a role". A role list
     * here is what produced 11 PLATFORM_ADMIN-only endpoints, and it fails closed, so nothing but a
     * test notices. Asserted separately from the map so the failure message says why.
     */
    @Test
    void noEndpointAuthorisesByRoleName() {
        actualGuards().forEach((method, guard) ->
            assertThat(guard)
                .as("%s must not authorise by role name — five of the twelve roles this controller "
                  + "used to recite (CHIEF_AUDIT_EXECUTIVE, AUDIT_DIRECTOR, INTERNAL_AUDITOR_LEAD, "
                  + "AUDIT_COMMITTEE_MEMBER, BOARD_MEMBER) do not exist in iam.roles, and a role "
                  + "guard gets no PLATFORM_ADMIN bypass, so the guard was silently tighter than "
                  + "it read", method)
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole")
                .doesNotContain("hasAuthority"));
    }

    /**
     * Writes must not be readable-tier. The pre-F2-05 guards did separate the two, and the risk in
     * a bulk conversion is exactly that a write lands on {@code READ} — which would hand the audit
     * plan to all eleven {@code AUDIT:READ} holders, VIEWER included.
     */
    @Test
    void noWriteOrDeleteRunsOnTheReadPermission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((method, expected) -> {
            if (READ.equals(expected)) return;
            assertThat(actual.get(method))
                .as("%s changes state; AUDIT:READ is held by 11 roles including VIEWER", method)
                .isNotNull()
                .isNotEqualTo(READ);
        });
    }

    @Test
    void noEndpointEscapesTheInventory() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.keySet())
            .as("IaController endpoints not pinned here — add them to EXPECTED with a deliberate "
              + "permission, and remember the AUDIT catalogue has no APPROVE action")
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        // Anti-vacuity: 26 mapped endpoints when this was written; an empty scan passes the rest.
        assertThat(actual).hasSize(26);
        assertThat(actual.values()).doesNotContain("<NO GUARD>");
    }
}
