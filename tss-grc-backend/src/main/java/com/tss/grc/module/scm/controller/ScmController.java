package com.tss.grc.module.scm.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.scm.dto.*;
import com.tss.grc.module.scm.service.ScmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scm")
@RequiredArgsConstructor
@RequireModule("TPRM")
@Tag(name = "Supply Chain Management")
@SecurityRequirement(name = "bearerAuth")
public class ScmController {

    private final ScmService service;

    // ── Dashboard ──
    @GetMapping("/stats")
    @PreAuthorize("hasPermission('SCM','READ')")
    @Operation(summary = "Supply Chain dashboard statistics")
    public ScmDashboardStats stats() { return service.stats(); }

    // ── Vendors ──
    @GetMapping("/vendors")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<VendorDtos.Response> listVendors(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.listVendors(pageable);
    }

    @GetMapping("/vendors/all")
    @PreAuthorize("hasPermission('SCM','READ')")
    @Operation(summary = "List all vendors (no pagination — for dropdowns)")
    public List<VendorDtos.Response> allVendors() { return service.allVendors(); }

    @GetMapping("/vendors/{id}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public VendorDtos.Response getVendor(@PathVariable Long id) { return service.getVendor(id); }

    @PostMapping("/vendors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public VendorDtos.Response createVendor(@Valid @RequestBody VendorDtos.Request request) {
        return service.createVendor(request);
    }

    @PutMapping("/vendors/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public VendorDtos.Response updateVendor(@PathVariable Long id, @Valid @RequestBody VendorDtos.Request request) {
        return service.updateVendor(id, request);
    }

    @DeleteMapping("/vendors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('SCM','DELETE')")
    public void deleteVendor(@PathVariable Long id) { service.deleteVendor(id); }

    // ── Onboarding ──
    @GetMapping("/onboarding")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<OnboardingDtos.Response> listOnboarding(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.listOnboarding(pageable);
    }

    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public OnboardingDtos.Response startOnboarding(@RequestBody OnboardingDtos.Request request) {
        return service.startOnboarding(request);
    }

    @PostMapping("/onboarding/{id}/advance")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    @Operation(summary = "Advance onboarding to the next workflow stage")
    public OnboardingDtos.Response advanceOnboarding(@PathVariable Long id) {
        return service.advanceOnboarding(id);
    }

    @PostMapping("/onboarding/{id}/approve")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public OnboardingDtos.Response approveOnboarding(@PathVariable Long id) {
        return service.approveOnboarding(id);
    }

    @PostMapping("/onboarding/{id}/reject")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public OnboardingDtos.Response rejectOnboarding(@PathVariable Long id, @RequestBody Map<String,String> body) {
        return service.rejectOnboarding(id, body.getOrDefault("reason", ""));
    }

    // ── Assessments ──
    @GetMapping("/assessments")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<AssessmentDtos.Response> listAssessments(@PageableDefault(size = 20, sort = "assessmentDate") Pageable pageable) {
        return service.listAssessments(pageable);
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public AssessmentDtos.Response createAssessment(@RequestBody AssessmentDtos.Request request) {
        return service.createAssessment(request);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public AssessmentDtos.Response updateAssessment(@PathVariable Long id, @RequestBody AssessmentDtos.Request request) {
        return service.updateAssessment(id, request);
    }

    @PostMapping("/assessments/{id}/escalate")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    @Operation(summary = "Escalate this assessment finding to Enterprise Risk")
    public AssessmentDtos.Response escalateAssessment(@PathVariable Long id) {
        return service.escalateAssessment(id);
    }

    // ── Dependencies ──
    @GetMapping("/dependencies")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<DependencyDtos.Response> listDependencies(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.listDependencies(pageable);
    }

    @PostMapping("/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public DependencyDtos.Response createDependency(@RequestBody DependencyDtos.Request request) {
        return service.createDependency(request);
    }

    @PutMapping("/dependencies/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public DependencyDtos.Response updateDependency(@PathVariable Long id, @RequestBody DependencyDtos.Request request) {
        return service.updateDependency(id, request);
    }

    @DeleteMapping("/dependencies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('SCM','DELETE')")
    public void deleteDependency(@PathVariable Long id) { service.deleteDependency(id); }

    // ── Incidents ──
    @GetMapping("/incidents")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<IncidentDtos.Response> listIncidents(@PageableDefault(size = 20, sort = "reportedAt") Pageable pageable) {
        return service.listIncidents(pageable);
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public IncidentDtos.Response createIncident(@RequestBody IncidentDtos.Request request) {
        return service.createIncident(request);
    }

    @PostMapping("/incidents/{id}/activate-backup")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public IncidentDtos.Response activateBackup(@PathVariable Long id) {
        return service.activateBackup(id);
    }

    @PostMapping("/incidents/{id}/create-capa")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public IncidentDtos.Response createCapa(@PathVariable Long id) {
        return service.createCapa(id);
    }

    @PostMapping("/incidents/{id}/escalate")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public IncidentDtos.Response escalateIncident(@PathVariable Long id) {
        return service.escalateIncident(id);
    }

    @PostMapping("/incidents/{id}/resolve")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public IncidentDtos.Response resolveIncident(@PathVariable Long id, @RequestBody Map<String,String> body) {
        return service.resolveIncident(id, body.getOrDefault("notes", ""));
    }

    // ── Contracts ──
    @GetMapping("/contracts")
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<ContractDtos.Response> listContracts(@PageableDefault(size = 20, sort = "endDate") Pageable pageable) {
        return service.listContracts(pageable);
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ContractDtos.Response createContract(@RequestBody ContractDtos.Request request) {
        return service.createContract(request);
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ContractDtos.Response updateContract(@PathVariable Long id, @RequestBody ContractDtos.Request request) {
        return service.updateContract(id, request);
    }
}
