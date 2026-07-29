package com.tss.grc.module.workflow.requests.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-13b — a decision on a workflow request must need approval authority, not participation.
 *
 * <p>Measured before the change: {@code WORKFLOW:WRITE} was held by <b>55 of 57 roles</b> and guarded
 * {@code /approve} and {@code /reject}, so VIEWER, REGULATOR_VIEWER, EXTERNAL_AUDITOR, SUPPLIER_USER
 * and all eight {@code *_INGEST} machine accounts could approve any request. {@code WORKFLOW:APPROVE}
 * existed, was held by 3 roles, and already guarded the same act in three sibling controllers.
 *
 * <p>The assertion is on the <b>annotation</b> rather than through MockMvc on purpose: the defect was
 * a guard string, the fix is a guard string, and a reflective check cannot pass because a mock let
 * something through. It also names the endpoints that deliberately did NOT move, so widening or
 * narrowing that set later is a visible decision rather than a quiet drift.
 */
class WorkflowApproveAuthorityTest {

    /** path suffix -> the permission its guard must name. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // The decision surface — moved by F2-13b.
        EXPECTED.put("/{id}/approve", "APPROVE");
        EXPECTED.put("/{id}/reject",  "APPROVE");
        // Participation — deliberately unchanged. A SUPPLIER_USER responding to a vendor attestation
        // and an ingest account raising a request both need exactly this, so revoking it to tidy a
        // report row would break real flows.
        EXPECTED.put("/{id}/submit",    "WRITE");
        EXPECTED.put("/{id}/resubmit",  "WRITE");
        EXPECTED.put("/{id}/respond",   "WRITE");
        EXPECTED.put("/{id}/send",      "WRITE");
        // Reviewer gate: `reviewer_id` and `approver_id` are separate columns on workflow.requests,
        // so reviewer and approver are separate roles by design. Requiring APPROVE here would
        // collapse a two-stage gate into one.
        EXPECTED.put("/{id}/review",    "WRITE");
        // Finalising. Whether this should need approval authority is a fair question and NOT the
        // reported defect; pinned so the answer is chosen rather than drifted into.
        EXPECTED.put("/{id}/close",     "WRITE");
        EXPECTED.put("/{id}/reopen",    "WRITE");
        EXPECTED.put("/{id}/escalate",  "WRITE");
    }

    private static Optional<Method> handlerFor(String path) {
        return Arrays.stream(WorkflowRequestController.class.getDeclaredMethods())
            .filter(m -> {
                PostMapping pm = m.getAnnotation(PostMapping.class);
                return pm != null && Arrays.asList(pm.value()).contains(path);
            })
            .findFirst();
    }

    @Test
    void everyLifecycleEndpointNamesThePermissionItShould() {
        EXPECTED.forEach((path, action) -> {
            Method handler = handlerFor(path).orElseThrow(
                () -> new AssertionError("no @PostMapping(\"" + path + "\") on WorkflowRequestController "
                    + "— the endpoint was renamed or removed, so this expectation is stale"));
            PreAuthorize guard = handler.getAnnotation(PreAuthorize.class);
            assertThat(guard)
                .as("%s has no @PreAuthorize at all — a lifecycle write cannot be unguarded", path)
                .isNotNull();
            assertThat(guard.value())
                .as("%s must be guarded by WORKFLOW:%s", path, action)
                .isEqualTo("hasPermission('WORKFLOW','" + action + "')");
        });
    }

    @Test
    void theDecisionEndpointsDoNotAcceptParticipationAuthority() {
        // Stated separately and positively, because this is the finding: holding WRITE must not be
        // enough to approve. If someone re-adds an `or hasPermission('WORKFLOW','WRITE')` leg to make
        // a 403 go away, the map test above still passes on the equality — but this does not.
        for (String path : new String[]{"/{id}/approve", "/{id}/reject"}) {
            String guard = handlerFor(path).orElseThrow().getAnnotation(PreAuthorize.class).value();
            assertThat(guard)
                .as("%s must not fall back to WRITE — 55 of 57 roles hold it", path)
                .doesNotContain("'WRITE'");
        }
    }

    @Test
    void theEndpointInventoryHasNotGrownBehindThisTest() {
        // A new lifecycle POST added without a guard decision would otherwise be invisible here.
        long lifecyclePosts = Arrays.stream(WorkflowRequestController.class.getDeclaredMethods())
            .map(m -> m.getAnnotation(PostMapping.class))
            .filter(pm -> pm != null)
            .flatMap(pm -> Arrays.stream(pm.value()))
            .filter(v -> v.startsWith("/{id}/"))
            .count();
        assertThat(lifecyclePosts)
            .as("a new /{id}/… action appeared on the workflow request lifecycle. Decide whether it "
                + "is a DECISION (WORKFLOW:APPROVE) or PARTICIPATION (WORKFLOW:WRITE) and add it to "
                + "EXPECTED — do not just bump this number")
            .isEqualTo(EXPECTED.size());
    }
}
