package com.tss.grc.module.ai.controller;

import com.tss.grc.module.ai.entity.AiSuggestion;
import com.tss.grc.module.ai.service.AiSuggestionService;
import com.tss.grc.module.ai.service.AiSuggestionService.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Saudi GRC implementation prompt §27 — AI-assisted features.
 *
 * <pre>
 *   POST /api/v1/ai/risk-description
 *   POST /api/v1/ai/bia-rto-rpo
 *   POST /api/v1/ai/evidence-quality
 *   POST /api/v1/ai/incident-root-cause
 *   POST /api/v1/ai/board-summary
 *
 *   GET  /api/v1/ai/suggestions?feature=...
 *   GET  /api/v1/ai/suggestions/entity/{entity}/{id}
 *   POST /api/v1/ai/suggestions/{id}/review  {decision, reviewerNote}
 * </pre>
 *
 * <p>Every endpoint persists a draft {@link AiSuggestion} that a human
 * must accept or reject — no AI output ever mutates a real record on its
 * own. The current generator is rule-based; the API stays identical when
 * an LLM gateway is wired in a future round.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
@Tag(name = "AI-assisted features (Saudi GRC §27)")
public class AiSuggestionController {

    private final AiSuggestionService service;

    @PostMapping("/risk-description")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion riskDescription(@RequestBody RiskDescriptionInput body) {
        return service.suggestRiskDescription(body);
    }

    @PostMapping("/bia-rto-rpo")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion biaRecommendation(@RequestBody BiaRecommendationInput body) {
        return service.suggestBiaRtoRpo(body);
    }

    @PostMapping("/evidence-quality")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion evidenceQuality(@RequestBody EvidenceQualityInput body) {
        return service.reviewEvidenceQuality(body);
    }

    @PostMapping("/incident-root-cause")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion incidentRootCause(@RequestBody IncidentRootCauseInput body) {
        return service.draftIncidentRootCause(body);
    }

    @PostMapping("/board-summary")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion boardSummary(@RequestBody BoardSummaryInput body) {
        return service.draftBoardSummary(body);
    }

    @PostMapping("/workflow-submission-review")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion workflowSubmissionReview(@RequestBody WorkflowSubmissionReviewInput body) {
        return service.suggestWorkflowSubmissionReview(body);
    }

    /**
     * List this tenant's suggestions for one feature.
     *
     * <p><b>Guard was {@code isAuthenticated()}.</b> Every write on this controller asks for
     * {@code AI:WRITE}, and {@code AI:READ} exists in the catalogue and is granted to 41 of 57
     * roles — so the platform already had a stated answer for who may read AI output, and these
     * two reads were the only ones not asking it. Measured live before the change, as
     * {@code viewer}, a persona holding <b>no AI permission of any kind</b>:
     *
     * <pre>
     *   POST /api/v1/ai/board-summary                    -> 403   writes were guarded
     *   GET  /api/v1/ai/suggestions?feature=BOARD_SUMMARY -> 200   10 rows, full outputJson
     *        ...containing open HIGH-risk counts, overdue CAPA, NCA and PDPL compliance
     *        percentages, BCM readiness — the executive posture summary.
     *   GET  /api/v1/ai/suggestions/entity/CrisisEvent/1  -> 200   1 row
     * </pre>
     *
     * <p>The 16 roles without {@code AI:READ} are exactly the set this content should not reach:
     * {@code VIEWER}, {@code EXTERNAL_AUDITOR}, {@code REGULATOR_VIEWER}, {@code SUPPLIER_USER},
     * and the eight {@code *_INGEST} machine accounts (plus {@code AUDITOR},
     * {@code INTERNAL_AUDITOR}, {@code CHANGE_MANAGER}, {@code EMERGENCY_RESPONDER}). Tenant
     * scoping was never the problem — both queries already filter {@code organisation_id}; the
     * gap was *within* a tenant.
     *
     * <p>No migration: {@code AI:READ} already exists and every intended reader already holds it.
     *
     * <p>⚠ <b>What this does NOT do, deliberately.</b> A suggestion records the record it was
     * derived from ({@code relatedEntity}/{@code relatedEntityId}), so the stricter SCR-004 rule
     * would be to inherit *that record's* read guard — an {@code INCIDENT_ROOT_CAUSE} draft would
     * then need the incident's own permission, not merely {@code AI:READ}. That is a real gap and
     * it is recorded as a follow-up rather than invented here, because it cannot simply reuse
     * {@code AttachmentAccessPolicy}: {@code relatedEntity} takes values like {@code "Dashboard"}
     * that are not records at all and appear in no parent registry, so a naive reuse would 403
     * everyone on the board summary. Inventing the rule is how six of seven prescriptions in this
     * programme got falsified.
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasPermission('AI','READ')")
    public List<AiSuggestion> listByFeature(@RequestParam String feature) {
        return service.listByFeature(feature);
    }

    /** See {@link #listByFeature} — same finding, same guard. {@code entity} is client-supplied. */
    @GetMapping("/suggestions/entity/{entity}/{id}")
    @PreAuthorize("hasPermission('AI','READ')")
    public List<AiSuggestion> listByEntity(@PathVariable String entity, @PathVariable Long id) {
        return service.listByEntity(entity, id);
    }

    @PostMapping("/suggestions/{id}/review")
    @PreAuthorize("hasPermission('AI','WRITE')")
    public AiSuggestion review(@PathVariable Long id, @RequestBody ReviewBody body) {
        return service.review(id, body);
    }
}
