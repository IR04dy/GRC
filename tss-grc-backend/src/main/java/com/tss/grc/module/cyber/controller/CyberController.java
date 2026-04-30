package com.tss.grc.module.cyber.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.dto.CyberDashboardDto;
import com.tss.grc.module.cyber.entity.*;
import com.tss.grc.module.cyber.service.CyberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cyber")
@RequiredArgsConstructor
@Tag(name = "Cyber Security")
@SecurityRequirement(name = "bearerAuth")
public class CyberController {

    private final CyberService cyberService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long orgId() {
        return TenantContext.getCurrentTenant();
    }

    private Pageable pageOf(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // =========================================================================
    // Dashboard
    // =========================================================================

    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    @Operation(summary = "Cyber security dashboard summary")
    public ResponseEntity<CyberDashboardDto> getDashboard() {
        return ResponseEntity.ok(cyberService.getDashboard(orgId()));
    }

    // =========================================================================
    // Frameworks
    // =========================================================================

    @GetMapping("/frameworks")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<List<CyberFramework>> getFrameworks() {
        return ResponseEntity.ok(cyberService.getFrameworks(orgId()));
    }

    @PostMapping("/frameworks")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberFramework> saveFramework(@RequestBody CyberFramework framework) {
        return ResponseEntity.ok(cyberService.saveFramework(framework, orgId()));
    }

    // =========================================================================
    // Controls
    // =========================================================================

    @GetMapping("/controls")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<CyberControl>> getControls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getControls(orgId(), pageOf(page, size)));
    }

    @GetMapping("/controls/all")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<List<CyberControl>> getAllControls() {
        return ResponseEntity.ok(cyberService.getAllControls(orgId()));
    }

    @GetMapping("/controls/{id}")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<CyberControl> getControl(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.getControl(id));
    }

    @PostMapping("/controls")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberControl> saveControl(@RequestBody CyberControl control) {
        return ResponseEntity.ok(cyberService.saveControl(control, orgId()));
    }

    @PutMapping("/controls/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberControl> updateControl(@PathVariable Long id, @RequestBody CyberControl control) {
        return ResponseEntity.ok(cyberService.updateControl(id, control));
    }

    @DeleteMapping("/controls/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteControl(@PathVariable Long id) {
        cyberService.deleteControl(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Sub-Controls
    // =========================================================================

    @GetMapping("/sub-controls")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<?> getSubControls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long controlId) {
        if (controlId != null) {
            return ResponseEntity.ok(cyberService.getSubControlsByControl(orgId(), controlId, pageOf(page, size)));
        }
        return ResponseEntity.ok(cyberService.getSubControls(orgId(), pageOf(page, size)));
    }

    @PostMapping("/sub-controls")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberSubControl> saveSubControl(@RequestBody CyberSubControl subControl) {
        return ResponseEntity.ok(cyberService.saveSubControl(subControl, orgId()));
    }

    @PutMapping("/sub-controls/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberSubControl> updateSubControl(@PathVariable Long id, @RequestBody CyberSubControl subControl) {
        return ResponseEntity.ok(cyberService.updateSubControl(id, subControl));
    }

    @DeleteMapping("/sub-controls/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteSubControl(@PathVariable Long id) {
        cyberService.deleteSubControl(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Assessments
    // =========================================================================

    @GetMapping("/assessments")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<ControlAssessment>> getAssessments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getAssessments(orgId(), pageOf(page, size)));
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<ControlAssessment> getAssessment(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.getAssessment(id));
    }

    @PostMapping("/assessments")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<ControlAssessment> saveAssessment(@RequestBody ControlAssessment assessment) {
        return ResponseEntity.ok(cyberService.saveAssessment(assessment, orgId()));
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<ControlAssessment> updateAssessment(@PathVariable Long id, @RequestBody ControlAssessment assessment) {
        return ResponseEntity.ok(cyberService.updateAssessment(id, assessment));
    }

    @PostMapping("/assessments/{id}/approve")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<ControlAssessment> approveAssessment(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.approveAssessment(id));
    }

    @PostMapping("/assessments/{id}/convert-gap")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberActionPlan> convertGapToCapa(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.convertGapToCapa(id, orgId()));
    }

    @DeleteMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long id) {
        cyberService.deleteAssessment(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Vulnerabilities
    // =========================================================================

    @GetMapping("/vulnerabilities")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<Vulnerability>> getVulnerabilities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getVulnerabilities(orgId(), pageOf(page, size)));
    }

    @GetMapping("/vulnerabilities/{id}")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Vulnerability> getVulnerability(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.getVulnerability(id));
    }

    @PostMapping("/vulnerabilities")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Vulnerability> saveVulnerability(@RequestBody Vulnerability vulnerability) {
        return ResponseEntity.ok(cyberService.saveVulnerability(vulnerability, orgId()));
    }

    @PutMapping("/vulnerabilities/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Vulnerability> updateVulnerability(@PathVariable Long id, @RequestBody Vulnerability vulnerability) {
        return ResponseEntity.ok(cyberService.updateVulnerability(id, vulnerability));
    }

    @PostMapping("/vulnerabilities/{id}/escalate")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Vulnerability> escalate(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.escalate(id));
    }

    @DeleteMapping("/vulnerabilities/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteVulnerability(@PathVariable Long id) {
        cyberService.deleteVulnerability(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Exceptions
    // =========================================================================

    @GetMapping("/exceptions")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<VulnException>> getExceptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getExceptions(orgId(), pageOf(page, size)));
    }

    @PostMapping("/exceptions")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<VulnException> saveException(@RequestBody VulnException exception) {
        return ResponseEntity.ok(cyberService.saveException(exception, orgId()));
    }

    @PutMapping("/exceptions/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<VulnException> updateException(@PathVariable Long id, @RequestBody VulnException exception) {
        return ResponseEntity.ok(cyberService.updateException(id, exception));
    }

    @PostMapping("/exceptions/{id}/approve")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<VulnException> approveException(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.approveException(id));
    }

    @DeleteMapping("/exceptions/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteException(@PathVariable Long id) {
        cyberService.deleteException(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Incidents
    // =========================================================================

    @GetMapping("/incidents")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<CyberIncident>> getIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getIncidents(orgId(), pageOf(page, size)));
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<CyberIncident> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.getIncident(id));
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberIncident> saveIncident(@RequestBody CyberIncident incident) {
        return ResponseEntity.ok(cyberService.saveIncident(incident, orgId()));
    }

    @PutMapping("/incidents/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberIncident> updateIncident(@PathVariable Long id, @RequestBody CyberIncident incident) {
        return ResponseEntity.ok(cyberService.updateIncident(id, incident));
    }

    @PostMapping("/incidents/{id}/start-response")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberIncident> startResponse(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.startResponse(id));
    }

    @DeleteMapping("/incidents/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteIncident(@PathVariable Long id) {
        cyberService.deleteIncident(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Assets
    // =========================================================================

    @GetMapping("/assets")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<CyberAsset>> getAssets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getAssets(orgId(), pageOf(page, size)));
    }

    @GetMapping("/assets/all")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<List<CyberAsset>> getAllAssets() {
        return ResponseEntity.ok(cyberService.getAllAssets(orgId()));
    }

    @PostMapping("/assets")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberAsset> saveAsset(@RequestBody CyberAsset asset) {
        return ResponseEntity.ok(cyberService.saveAsset(asset, orgId()));
    }

    @PutMapping("/assets/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberAsset> updateAsset(@PathVariable Long id, @RequestBody CyberAsset asset) {
        return ResponseEntity.ok(cyberService.updateAsset(id, asset));
    }

    @DeleteMapping("/assets/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        cyberService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Evidence
    // =========================================================================

    @GetMapping("/evidence")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<CyberEvidence>> getEvidence(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getEvidence(orgId(), pageOf(page, size)));
    }

    @PostMapping("/evidence")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberEvidence> saveEvidence(@RequestBody CyberEvidence evidence) {
        return ResponseEntity.ok(cyberService.saveEvidence(evidence, orgId()));
    }

    @PutMapping("/evidence/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberEvidence> updateEvidence(@PathVariable Long id, @RequestBody CyberEvidence evidence) {
        return ResponseEntity.ok(cyberService.updateEvidence(id, evidence));
    }

    @DeleteMapping("/evidence/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteEvidence(@PathVariable Long id) {
        cyberService.deleteEvidence(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Action Plans
    // =========================================================================

    @GetMapping("/action-plans")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<Page<CyberActionPlan>> getActionPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cyberService.getActionPlans(orgId(), pageOf(page, size)));
    }

    @GetMapping("/action-plans/{id}")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public ResponseEntity<CyberActionPlan> getActionPlan(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.getActionPlan(id));
    }

    @PostMapping("/action-plans")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberActionPlan> saveActionPlan(@RequestBody CyberActionPlan actionPlan) {
        return ResponseEntity.ok(cyberService.saveActionPlan(actionPlan, orgId()));
    }

    @PutMapping("/action-plans/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberActionPlan> updateActionPlan(@PathVariable Long id, @RequestBody CyberActionPlan actionPlan) {
        return ResponseEntity.ok(cyberService.updateActionPlan(id, actionPlan));
    }

    @PostMapping("/action-plans/{id}/complete")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CyberActionPlan> completeActionPlan(@PathVariable Long id) {
        return ResponseEntity.ok(cyberService.completeActionPlan(id));
    }

    @DeleteMapping("/action-plans/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<Void> deleteActionPlan(@PathVariable Long id) {
        cyberService.deleteActionPlan(id);
        return ResponseEntity.noContent().build();
    }
}
