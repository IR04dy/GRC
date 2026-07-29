package com.tss.grc.module.workflow.requests.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recipient-list CRUD must separate delete from write.
 *
 * <h3>What was wrong</h3>
 * <p>`WORKFLOW:DELETE` was catalogued, held by exactly <b>one</b> role (ORG_ADMIN), and named by no
 * {@code @PreAuthorize} anywhere — one of the 14 "catalogued and granted, but guarding no endpoint"
 * rows in {@code Appendix/GRANT_TIGHTNESS.md}, and the only state-changing one. Deleting a saved
 * distribution list ran on {@code WORKFLOW:WRITE}, which F2-13b measured at <b>55 of 57 roles</b>
 * and deliberately left that wide, because after the approve/write split it means *participate*.
 * That population includes VIEWER, REGULATOR_VIEWER, EXTERNAL_AUDITOR, SUPPLIER_USER and all eight
 * {@code *_INGEST} machine accounts.
 *
 * <p>**V325** made the permission satisfiable before the guard demanded it — 1 → 12 holders,
 * derived (already holds some module's DELETE *and* already holds `WORKFLOW:WRITE`) rather than
 * hand-picked. Lifting the guard alone would have left deletion to ORG_ADMIN.
 *
 * <h3>Why this test</h3>
 * <p>The defect was a permission nothing referenced, so the regression to prevent is the guard
 * drifting back to WRITE — an annotation-level fact. Same shape as {@code AiSuggestionGuardTest}
 * (F2-50) and {@code WorkflowApproveAuthorityTest} (F2-13b).
 */
class WorkflowRecipientListGuardTest {

    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("list",   "hasPermission('WORKFLOW','READ')");
        EXPECTED.put("get",    "hasPermission('WORKFLOW','READ')");
        EXPECTED.put("create", "hasPermission('WORKFLOW','WRITE')");
        EXPECTED.put("update", "hasPermission('WORKFLOW','WRITE')");
        // the finding: destroying shared configuration is not participating in a workflow
        EXPECTED.put("delete", "hasPermission('WORKFLOW','DELETE')");
    }

    private static Map<String, String> actualGuards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : WorkflowRecipientListController.class.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
            if (!mapped) continue;
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    @Test
    void every_endpoint_carries_the_expected_workflow_permission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((method, guard) ->
            assertThat(actual)
                .as("%s must be guarded by %s", method, guard)
                .containsEntry(method, guard));
    }

    /**
     * The delete specifically must not drift back to {@code WRITE} — the exact expression the
     * defect wore, and the one a reader would most plausibly "restore" for consistency with its
     * CRUD siblings. Asserted separately so the failure names the regression.
     */
    @Test
    void delete_does_not_run_on_the_write_permission() {
        assertThat(actualGuards().get("delete"))
            .as("Deleting a shared recipient list must not be authorised by WORKFLOW:WRITE — that "
              + "is held by 55 of 57 roles including read-only, external and machine personas")
            .isNotNull()
            .doesNotContain("'WRITE'");
    }

    @Test
    void no_endpoint_escapes_the_inventory() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.keySet())
            .as("WorkflowRecipientListController endpoints not pinned here — add them to EXPECTED "
              + "with a deliberate guard decision")
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        // Anti-vacuity: 5 mapped endpoints when this was written; an empty scan would pass above.
        assertThat(actual).hasSize(5);
        assertThat(actual.values()).doesNotContain("<NO GUARD>");
    }
}
