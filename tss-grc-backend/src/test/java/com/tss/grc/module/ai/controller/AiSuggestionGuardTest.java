package com.tss.grc.module.ai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every endpoint on {@link AiSuggestionController} must ask for an AI permission.
 *
 * <h3>What was wrong</h3>
 * <p>The two suggestion reads were {@code isAuthenticated()} while every write asked for
 * {@code AI:WRITE} — and {@code AI:READ} existed in the catalogue, granted to 41 of 57 roles. So the
 * platform had a stated answer for who may read AI output and these routes did not ask it. Measured
 * live as {@code viewer}, holding no AI permission at all: refused the write (403), handed 10
 * board-summary drafts with full {@code outputJson} (open HIGH-risk counts, overdue CAPA, NCA/PDPL
 * compliance percentages) on the read.
 *
 * <h3>Why a reflective test rather than a `@WebMvcTest`</h3>
 * <p>The defect was a *missing* guard, and the thing that has to not regress is the annotation
 * itself. Asserting on the annotation catches a new endpoint added without a guard, which is how
 * these two came to exist; a request-level test only covers routes someone remembered to write a
 * case for. Same shape as {@code WorkflowApproveAuthorityTest} (F2-13b).
 */
class AiSuggestionGuardTest {

    /**
     * Every mapped method and the guard it must carry. Reads take {@code AI:READ}, writes take
     * {@code AI:WRITE}. Listed exhaustively rather than pattern-matched so that adding an endpoint
     * forces an edit here — the inventory test below is what makes that mandatory.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // reads — the two that were isAuthenticated()
        EXPECTED.put("listByFeature", "hasPermission('AI','READ')");
        EXPECTED.put("listByEntity",  "hasPermission('AI','READ')");
        // generators + the human accept/reject
        EXPECTED.put("riskDescription",          "hasPermission('AI','WRITE')");
        EXPECTED.put("biaRecommendation",        "hasPermission('AI','WRITE')");
        EXPECTED.put("evidenceQuality",          "hasPermission('AI','WRITE')");
        EXPECTED.put("incidentRootCause",        "hasPermission('AI','WRITE')");
        EXPECTED.put("boardSummary",             "hasPermission('AI','WRITE')");
        EXPECTED.put("workflowSubmissionReview", "hasPermission('AI','WRITE')");
        EXPECTED.put("review",                   "hasPermission('AI','WRITE')");
    }

    private static Map<String, String> actualGuards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : AiSuggestionController.class.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(GetMapping.class) && !m.isAnnotationPresent(PostMapping.class)) continue;
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    @Test
    void every_endpoint_carries_the_expected_ai_permission() {
        Map<String, String> actual = actualGuards();
        EXPECTED.forEach((method, guard) ->
            assertThat(actual)
                .as("%s must be guarded by %s", method, guard)
                .containsEntry(method, guard));
    }

    /**
     * The two reads specifically must not drift back to {@code isAuthenticated()} — the exact
     * expression the defect wore. Asserted separately from the map above so the failure message
     * names the regression rather than a generic mismatch.
     */
    @Test
    void the_suggestion_reads_are_not_authenticated_only() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.get("listByFeature"))
            .as("GET /suggestions must not be authenticated-only — a persona with no AI grant read "
              + "10 board summaries through it")
            .isNotNull()
            .doesNotContain("isAuthenticated");
        assertThat(actual.get("listByEntity"))
            .as("GET /suggestions/entity/{entity}/{id} must not be authenticated-only")
            .isNotNull()
            .doesNotContain("isAuthenticated");
    }

    /**
     * Inventory: no mapped endpoint may exist on this controller without an entry above. Stops a new
     * route being added with no guard, or with a guard nobody compared against the others — which is
     * exactly how the two reads survived.
     */
    @Test
    void no_endpoint_escapes_the_inventory() {
        Map<String, String> actual = actualGuards();
        assertThat(actual.keySet())
            .as("AiSuggestionController endpoints not pinned by this test — add them to EXPECTED "
              + "with a deliberate guard decision")
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        // Anti-vacuity: if reflection stopped finding methods, the assertions above pass over
        // nothing. The controller had 9 mapped endpoints when this was written.
        assertThat(actual).hasSize(9);
        assertThat(actual.values()).doesNotContain("<NO GUARD>");
    }
}
