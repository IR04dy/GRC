package com.tss.grc.module.platform.controls.controller;

import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlAssessment;
import com.tss.grc.module.platform.controls.entity.ControlMapping;
import com.tss.grc.module.platform.controls.entity.ControlObjective;
import com.tss.grc.module.platform.controls.entity.RiskControlLink;
import com.tss.grc.module.platform.controls.service.ControlLibraryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Unified Control Library REST surface — F2.
 *
 * <pre>
 *   GET    /api/v1/controls                 list (filter: domain, status)
 *   GET    /api/v1/controls/:id             get one
 *   POST   /api/v1/controls                 create
 *   PUT    /api/v1/controls/:id             update
 *   POST   /api/v1/controls/:id/transition  DRAFT → ACTIVE → RETIRED
 *   DELETE /api/v1/controls/:id             soft delete
 *
 *   GET    /api/v1/control-objectives                list (filter: domain)
 *   POST   /api/v1/control-objectives                create
 *   GET    /api/v1/control-objectives/:id/controls   list controls under objective
 *
 *   GET    /api/v1/controls/:id/mappings             list citation mappings
 *   GET    /api/v1/citations/:id/mappings            list controls covering a citation
 *   POST   /api/v1/control-mappings                  create mapping
 *   PUT    /api/v1/control-mappings/:id              update
 *   DELETE /api/v1/control-mappings/:id              soft delete
 *   GET    /api/v1/control-mappings/coverage         distinct authority docs covered
 *
 *   GET    /api/v1/risks/:riskId/control-links       list links for a risk
 *   GET    /api/v1/controls/:id/risk-links           list links for a control
 *   POST   /api/v1/risk-control-links                create link
 *   PUT    /api/v1/risk-control-links/:id            update
 *   DELETE /api/v1/risk-control-links/:id            soft delete
 *
 *   GET    /api/v1/controls/:id/assessments          list assessments
 *   POST   /api/v1/control-assessments               create
 *   POST   /api/v1/control-assessments/:id/transition workflow walk
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Controls — Unified Library (F2)")
public class ControlLibraryController {

    private final ControlLibraryService service;

    // ─── Controls ─────────────────────────────────────────────────

    @GetMapping("/controls")
    @PreAuthorize("isAuthenticated()")
    public List<Control> listControls(@RequestParam(required = false) String domain,
                                      @RequestParam(required = false) String status) {
        return service.listControls(domain, status);
    }

    @GetMapping("/controls/{id}")
    @PreAuthorize("isAuthenticated()")
    public Control getControl(@PathVariable Long id) { return service.getControl(id); }

    @PostMapping("/controls")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Control> createControl(@RequestBody Control body) {
        return ResponseEntity.ok(service.createControl(body));
    }

    @PutMapping("/controls/{id}")
    @PreAuthorize("isAuthenticated()")
    public Control updateControl(@PathVariable Long id, @RequestBody Control body) {
        return service.updateControl(id, body);
    }

    public record TransitionBody(String to) {}

    @PostMapping("/controls/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public Control transitionControl(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionControl(id, body.to());
    }

    @DeleteMapping("/controls/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteControl(@PathVariable Long id) {
        service.deleteControl(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Objectives ───────────────────────────────────────────────

    @GetMapping("/control-objectives")
    @PreAuthorize("isAuthenticated()")
    public List<ControlObjective> listObjectives(@RequestParam(required = false) String domain) {
        return service.listObjectives(domain);
    }

    @PostMapping("/control-objectives")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ControlObjective> createObjective(@RequestBody ControlObjective body) {
        return ResponseEntity.ok(service.createObjective(body));
    }

    @GetMapping("/control-objectives/{id}/controls")
    @PreAuthorize("isAuthenticated()")
    public List<Control> listObjectiveControls(@PathVariable Long id) {
        return service.listControlsForObjective(id);
    }

    // ─── Mappings ─────────────────────────────────────────────────

    @GetMapping("/controls/{id}/mappings")
    @PreAuthorize("isAuthenticated()")
    public List<ControlMapping> listControlMappings(@PathVariable Long id) {
        return service.listMappingsForControl(id);
    }

    @GetMapping("/citations/{id}/mappings")
    @PreAuthorize("isAuthenticated()")
    public List<ControlMapping> listCitationMappings(@PathVariable Long id) {
        return service.listMappingsForCitation(id);
    }

    @PostMapping("/control-mappings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ControlMapping> createMapping(@RequestBody ControlMapping body) {
        return ResponseEntity.ok(service.createMapping(body));
    }

    @PutMapping("/control-mappings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ControlMapping updateMapping(@PathVariable Long id, @RequestBody ControlMapping body) {
        return service.updateMapping(id, body);
    }

    @DeleteMapping("/control-mappings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        service.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/control-mappings/coverage")
    @PreAuthorize("isAuthenticated()")
    public List<Long> mappingCoverage() {
        return service.distinctMappedAuthorityDocumentIds();
    }

    // ─── Risk ↔ Control links ─────────────────────────────────────

    @GetMapping("/risks/{riskId}/control-links")
    @PreAuthorize("isAuthenticated()")
    public List<RiskControlLink> listLinksForRisk(@PathVariable Long riskId) {
        return service.listLinksForRisk(riskId);
    }

    @GetMapping("/controls/{id}/risk-links")
    @PreAuthorize("isAuthenticated()")
    public List<RiskControlLink> listLinksForControl(@PathVariable Long id) {
        return service.listLinksForControl(id);
    }

    @PostMapping("/risk-control-links")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RiskControlLink> linkRiskToControl(@RequestBody RiskControlLink body) {
        return ResponseEntity.ok(service.linkRiskToControl(body));
    }

    @PutMapping("/risk-control-links/{id}")
    @PreAuthorize("isAuthenticated()")
    public RiskControlLink updateLink(@PathVariable Long id, @RequestBody RiskControlLink body) {
        return service.updateLink(id, body);
    }

    @DeleteMapping("/risk-control-links/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteLink(@PathVariable Long id) {
        service.deleteLink(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Assessments ──────────────────────────────────────────────

    @GetMapping("/controls/{id}/assessments")
    @PreAuthorize("isAuthenticated()")
    public List<ControlAssessment> listAssessments(@PathVariable Long id) {
        return service.listAssessmentsForControl(id);
    }

    @PostMapping("/control-assessments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ControlAssessment> createAssessment(@RequestBody ControlAssessment body) {
        return ResponseEntity.ok(service.createAssessment(body));
    }

    @PostMapping("/control-assessments/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public ControlAssessment transitionAssessment(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transitionAssessment(id, body.to());
    }
}
