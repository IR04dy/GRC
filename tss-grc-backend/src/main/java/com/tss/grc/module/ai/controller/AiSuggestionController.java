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
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion riskDescription(@RequestBody RiskDescriptionInput body) {
        return service.suggestRiskDescription(body);
    }

    @PostMapping("/bia-rto-rpo")
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion biaRecommendation(@RequestBody BiaRecommendationInput body) {
        return service.suggestBiaRtoRpo(body);
    }

    @PostMapping("/evidence-quality")
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion evidenceQuality(@RequestBody EvidenceQualityInput body) {
        return service.reviewEvidenceQuality(body);
    }

    @PostMapping("/incident-root-cause")
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion incidentRootCause(@RequestBody IncidentRootCauseInput body) {
        return service.draftIncidentRootCause(body);
    }

    @PostMapping("/board-summary")
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion boardSummary(@RequestBody BoardSummaryInput body) {
        return service.draftBoardSummary(body);
    }

    @PostMapping("/workflow-submission-review")
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion workflowSubmissionReview(@RequestBody WorkflowSubmissionReviewInput body) {
        return service.suggestWorkflowSubmissionReview(body);
    }

    @GetMapping("/suggestions")
    @PreAuthorize("isAuthenticated()")
    public List<AiSuggestion> listByFeature(@RequestParam String feature) {
        return service.listByFeature(feature);
    }

    @GetMapping("/suggestions/entity/{entity}/{id}")
    @PreAuthorize("isAuthenticated()")
    public List<AiSuggestion> listByEntity(@PathVariable String entity, @PathVariable Long id) {
        return service.listByEntity(entity, id);
    }

    @PostMapping("/suggestions/{id}/review")
    @PreAuthorize("isAuthenticated()")
    public AiSuggestion review(@PathVariable Long id, @RequestBody ReviewBody body) {
        return service.review(id, body);
    }
}
