package com.tss.grc.module.risk.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk.dto.RiskAssessmentRequest;
import com.tss.grc.module.risk.dto.RiskTreatmentRequest;
import com.tss.grc.module.risk.dto.RiskTreatmentUpdateRequest;
import com.tss.grc.module.risk.entity.RiskAssessment;
import com.tss.grc.module.risk.entity.RiskTreatment;
import com.tss.grc.module.risk.service.RiskAssessmentService;
import com.tss.grc.module.risk.service.RiskTreatmentService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Risk-Advanced REST endpoints — re-assessment history + treatment plans.
 *
 * <pre>
 *   GET    /api/v1/risk/risks/{riskId}/assessments        chronological
 *   POST   /api/v1/risk/risks/{riskId}/assessments        record new (promotes to residual)
 *   PUT    /api/v1/risk/assessments/{id}                  correct latest
 *
 *   GET    /api/v1/risk/risks/{riskId}/treatments         per-risk
 *   POST   /api/v1/risk/risks/{riskId}/treatments         create
 *   PUT    /api/v1/risk/treatments/{id}                   update (description / owner / due)
 *   POST   /api/v1/risk/treatments/{id}/transition        body {to}
 *   DELETE /api/v1/risk/treatments/{id}                   soft-delete (Rule T1 enforced)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@RequireModule("RISK")
@Tag(name = "Risk-Advanced — assessments & treatments")
public class RiskAdvancedController {

    private final RiskAssessmentService assessmentService;
    private final RiskTreatmentService treatmentService;

    // ─── Assessments ───────────────────────────────────────────────

    @GetMapping("/risks/{riskId}/assessments")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<RiskAssessment> listAssessments(@PathVariable Long riskId) {
        return assessmentService.listForRisk(riskId);
    }

    @PostMapping("/risks/{riskId}/assessments")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<RiskAssessment> recordAssessment(@PathVariable Long riskId,
                                                           @Valid @RequestBody RiskAssessmentRequest req,
                                                           @AuthenticationPrincipal GrcUserDetails principal) {
        // SEC-RISK-002: assessor comes from the token, not the body — attribution
        // must not be forgeable; assessmentDate/id/score are stamped server-side.
        Long assessorId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(assessmentService.record(
            riskId, req.likelihood(), req.impact(),
            req.assessmentNotesEn(), req.assessmentNotesAr(), assessorId));
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public RiskAssessment correctAssessment(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        Integer likelihood = body.get("likelihood") == null ? null : ((Number) body.get("likelihood")).intValue();
        Integer impact     = body.get("impact")     == null ? null : ((Number) body.get("impact")).intValue();
        String  notes      = (String) body.get("assessmentNotesEn");
        String  notesAr    = (String) body.get("assessmentNotesAr");
        return assessmentService.correct(id, likelihood, impact, notes, notesAr);
    }

    // ─── Treatments ────────────────────────────────────────────────

    @GetMapping("/risks/{riskId}/treatments")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<RiskTreatment> listTreatments(@PathVariable Long riskId) {
        return treatmentService.listForRisk(riskId);
    }

    @GetMapping("/treatments/{id}")
    @PreAuthorize("hasPermission('RISK','READ')")
    public RiskTreatment getTreatment(@PathVariable Long id) { return treatmentService.get(id); }

    @PostMapping("/risks/{riskId}/treatments")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<RiskTreatment> createTreatment(@PathVariable Long riskId,
                                                         @Valid @RequestBody RiskTreatmentRequest req) {
        // SEC-RISK-002: riskId from the path, status forced to PLANNED server-side.
        return ResponseEntity.ok(treatmentService.create(riskId, req));
    }

    @PutMapping("/treatments/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public RiskTreatment updateTreatment(@PathVariable Long id,
                                         @Valid @RequestBody RiskTreatmentUpdateRequest req) {
        // SEC-RISK-002: status/completedAt/riskId/treatmentType are not bindable here.
        return treatmentService.update(id, req);
    }

    @PostMapping("/treatments/{id}/transition")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public RiskTreatment transitionTreatment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return treatmentService.transition(id, body.get("to"));
    }

    @DeleteMapping("/treatments/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<Void> deleteTreatment(@PathVariable Long id) {
        treatmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
