package com.tss.grc.module.incident.controller;

import com.tss.grc.module.incident.dto.*;
import com.tss.grc.module.incident.service.IncidentService;
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

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident Management")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    @PreAuthorize("hasPermission('INCIDENT', 'READ')")
    @Operation(summary = "List incidents with optional filters")
    public Page<IncidentResponse> list(
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return incidentService.list(severity, status, category, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('INCIDENT', 'READ')")
    public IncidentResponse get(@PathVariable Long id) {
        return incidentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('INCIDENT', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@Valid @RequestBody IncidentRequest request) {
        return incidentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('INCIDENT', 'WRITE')")
    public IncidentResponse update(@PathVariable Long id, @Valid @RequestBody IncidentRequest request) {
        return incidentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('INCIDENT', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        incidentService.delete(id);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('INCIDENT', 'READ')")
    @Operation(summary = "Get incident counts by status and severity")
    public IncidentStats getStats() {
        return incidentService.getStats();
    }

    @GetMapping("/recent")
    @PreAuthorize("hasPermission('INCIDENT', 'READ')")
    @Operation(summary = "Get 5 most recent incidents for dashboard widget")
    public List<IncidentResponse> getRecent() {
        return incidentService.getRecent();
    }

    // ── NIST 800-61 lifecycle workflow ───────────────────────────────────────
    @PostMapping("/{id}/advance-phase")
    @PreAuthorize("hasPermission('INCIDENT', 'WRITE')")
    @Operation(summary = "Advance to the next NIST 800-61 phase (IDENTIFICATION → ANALYSIS → CONTAINMENT → ERADICATION → RECOVERY → POST_INCIDENT)")
    public java.util.Map<String, Object> advancePhase(@PathVariable Long id) {
        return incidentService.advancePhase(id);
    }

    @PostMapping("/{id}/set-phase")
    @PreAuthorize("hasPermission('INCIDENT', 'WRITE')")
    @Operation(summary = "Set the NIST phase explicitly (use sparingly — prefer /advance-phase)")
    public java.util.Map<String, Object> setPhase(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return incidentService.setPhase(id, body.getOrDefault("phase", ""));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('INCIDENT', 'WRITE')")
    public java.util.Map<String, Object> closeIncident(@PathVariable Long id) {
        return incidentService.closeIncident(id);
    }

    // ── Saudi regulator notification ─────────────────────────────────────────
    @PostMapping("/{id}/notify-regulator")
    @PreAuthorize("hasPermission('INCIDENT', 'WRITE')")
    @Operation(summary = "Stamp regulator notification timestamp. body.regulator ∈ {NCA_HEY, DATA_OFFICE, CITC, SAMA, HRSD}")
    public java.util.Map<String, Object> notifyRegulator(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return incidentService.notifyRegulator(id, body.getOrDefault("regulator", ""));
    }

    @GetMapping("/{id}/regulatory-templates")
    @PreAuthorize("hasPermission('INCIDENT', 'READ')")
    @Operation(summary = "Saudi regulatory notification templates (NCA HEY 24-hr, Data Office 72-hr, CITC 24-hr, SAMA, HRSD) auto-filled from this incident")
    public java.util.List<java.util.Map<String, Object>> regulatoryTemplates(@PathVariable Long id) {
        return incidentService.regulatoryTemplates(id);
    }
}
