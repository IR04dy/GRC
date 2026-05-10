package com.tss.grc.module.evidence.controller;

import com.tss.grc.module.evidence.entity.ConnectorDefinition;
import com.tss.grc.module.evidence.entity.EvidenceVersion;
import com.tss.grc.module.evidence.service.EvidenceMatrixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints for the framework-tab × control-anchored evidence view, plus the
 * version history and connector-library MVP. Implements the buyer-assessment
 * §5.9 recommendations end-to-end.
 */
@RestController
@RequestMapping("/api/v1/evidence")
@RequiredArgsConstructor
@Tag(name = "Evidence — Matrix, Versions & Connectors")
@SecurityRequirement(name = "bearerAuth")
public class EvidenceMatrixController {

    private final EvidenceMatrixService matrixService;

    @GetMapping("/matrix")
    @PreAuthorize("hasPermission('EVIDENCE','READ')")
    @Operation(summary = "Cross-framework matrix: each framework with its controls/sub-controls and per-row evidence count + freshness")
    public List<Map<String, Object>> matrix() {
        return matrixService.matrix();
    }

    @GetMapping("/items/{id}/versions")
    @PreAuthorize("hasPermission('EVIDENCE','READ')")
    @Operation(summary = "Version history for an evidence item — assessment §5.9 recommendation #1")
    public List<EvidenceVersion> versions(@PathVariable Long id) {
        return matrixService.versions(id);
    }

    @PostMapping("/items/{id}/snapshot")
    @PreAuthorize("hasPermission('EVIDENCE','WRITE')")
    @Operation(summary = "Snapshot the current evidence state into a new version row (extends WORM hash chain)")
    public EvidenceVersion snapshot(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return matrixService.snapshotVersion(id, body);
    }

    // ── Connector library MVP (§5.9 #2) ──────────────────────────────────────

    @GetMapping("/connectors")
    @PreAuthorize("hasPermission('EVIDENCE','READ')")
    public List<ConnectorDefinition> listConnectors() {
        return matrixService.listConnectors();
    }

    @PostMapping("/connectors/{id}/status")
    @PreAuthorize("hasPermission('EVIDENCE','WRITE')")
    public ConnectorDefinition setConnectorStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return matrixService.setConnectorStatus(id, body.getOrDefault("status", ""));
    }

    @PostMapping("/connectors/{id}/run")
    @PreAuthorize("hasPermission('EVIDENCE','WRITE')")
    @Operation(summary = "Trigger a (currently simulated) connector run; in production this would invoke the source-system API per the connector's collection plan.")
    public ConnectorDefinition runConnector(@PathVariable Long id) {
        return matrixService.runConnector(id);
    }
}
