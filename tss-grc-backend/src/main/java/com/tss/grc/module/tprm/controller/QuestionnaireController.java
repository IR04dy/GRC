package com.tss.grc.module.tprm.controller;

import com.tss.grc.module.tprm.entity.QuestionnaireQuestion;
import com.tss.grc.module.tprm.entity.QuestionnaireResponse;
import com.tss.grc.module.tprm.entity.QuestionnaireTemplate;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.service.QuestionnaireService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TPRM Questionnaire REST endpoints — V81.
 *
 * <pre>
 *   /api/v1/tprm/questionnaire-templates          CRUD + transition
 *   /api/v1/tprm/questionnaire-templates/:id/questions   list + create
 *   /api/v1/tprm/questionnaire-questions/:id      PUT update / DELETE
 *
 *   /api/v1/tprm/assessments/:id/assign-template  POST {templateId}
 *   /api/v1/tprm/assessments/:id/responses        GET / POST upsert
 *   /api/v1/tprm/assessments/:id/complete         POST → workflow_status=COMPLETED
 *   /api/v1/tprm/assessments/:id/review           POST → workflow_status=REVIEWED
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/tprm")
@RequiredArgsConstructor
@Tag(name = "TPRM — Questionnaire engine")
public class QuestionnaireController {

    private final QuestionnaireService service;

    private static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    // ─── Templates ─────────────────────────────────────────────────

    @GetMapping("/questionnaire-templates")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<QuestionnaireTemplate> listTemplates(@RequestParam(required = false) String status) {
        return service.listTemplates(status);
    }

    @GetMapping("/questionnaire-templates/{id}")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public QuestionnaireTemplate getTemplate(@PathVariable Long id) { return service.getTemplate(id); }

    @PostMapping("/questionnaire-templates")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ResponseEntity<QuestionnaireTemplate> createTemplate(@RequestBody QuestionnaireTemplate body) {
        return ResponseEntity.ok(service.createTemplate(body));
    }

    @PutMapping("/questionnaire-templates/{id}")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public QuestionnaireTemplate updateTemplate(@PathVariable Long id, @RequestBody QuestionnaireTemplate body) {
        return service.updateTemplate(id, body);
    }

    @PostMapping("/questionnaire-templates/{id}/transition")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public QuestionnaireTemplate transitionTemplate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.transitionTemplate(id, body.get("to"));
    }

    // ─── Questions ─────────────────────────────────────────────────

    @GetMapping("/questionnaire-templates/{templateId}/questions")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<QuestionnaireQuestion> listQuestions(@PathVariable Long templateId) {
        return service.listQuestions(templateId);
    }

    @PostMapping("/questionnaire-templates/{templateId}/questions")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ResponseEntity<QuestionnaireQuestion> createQuestion(@PathVariable Long templateId,
                                                                @RequestBody QuestionnaireQuestion body) {
        return ResponseEntity.ok(service.createQuestion(templateId, body));
    }

    @PutMapping("/questionnaire-questions/{id}")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public QuestionnaireQuestion updateQuestion(@PathVariable Long id, @RequestBody QuestionnaireQuestion body) {
        return service.updateQuestion(id, body);
    }

    @DeleteMapping("/questionnaire-questions/{id}")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        service.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Assessment workflow ───────────────────────────────────────

    @PostMapping("/assessments/{id}/assign-template")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public VendorAssessment assignTemplate(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return service.assignTemplate(id, body.get("templateId"));
    }

    @GetMapping("/assessments/{id}/responses")
    @PreAuthorize("hasPermission('TPRM','READ')")
    public List<QuestionnaireResponse> listResponses(@PathVariable Long id) {
        return service.listResponses(id);
    }

    @PostMapping("/assessments/{id}/responses")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public ResponseEntity<QuestionnaireResponse> upsertResponse(@PathVariable Long id,
                                                                @RequestBody QuestionnaireResponse body,
                                                                Authentication auth) {
        return ResponseEntity.ok(service.upsertResponse(id, body, actor(auth)));
    }

    @PostMapping("/assessments/{id}/complete")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public VendorAssessment markComplete(@PathVariable Long id) { return service.markComplete(id); }

    @PostMapping("/assessments/{id}/review")
    @PreAuthorize("hasPermission('TPRM','WRITE')")
    public VendorAssessment markReviewed(@PathVariable Long id) { return service.markReviewed(id); }
}
