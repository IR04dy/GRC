package com.tss.grc.module.change_mgmt.controller;

import com.tss.grc.module.change_mgmt.entity.*;
import com.tss.grc.module.change_mgmt.service.ChangeManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Change Management REST surface.
 *
 * Permission key is "CHANGE" (CM-C1, V301) — a dedicated module, so change
 * authorship/approval is no longer granted implicitly by the platform-shared
 * "WORKFLOW" gate. Read-only/observer/external and telemetry-ingest personas
 * are excluded from CHANGE write/approve/delete by the V301 grant matrix.
 */
@RestController
@RequestMapping("/api/v1/changes")
@RequiredArgsConstructor
@Tag(name = "Change Management — ITIL v4")
@SecurityRequirement(name = "bearerAuth")
public class ChangeManagementController {

    private final ChangeManagementService service;

    // ── Listing + CRUD ──────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public Page<ChangeRequest> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(status, type, risk, category, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public ChangeRequest get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequest create(@RequestBody ChangeRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    public ChangeRequest update(@PathVariable Long id, @RequestBody ChangeRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ── Lifecycle transition ────────────────────────────────────────────────

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @Operation(summary = "Move a change request to a target status; validates ITIL v4 transitions.")
    public ChangeRequest transition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        if (target == null) throw new IllegalArgumentException("target is required");
        return service.transition(id, target, body);
    }

    // ── Approvals (CAB) ─────────────────────────────────────────────────────

    @GetMapping("/{id}/approvals")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeApproval> approvals(@PathVariable Long id) {
        return service.approvals(id);
    }

    @PostMapping("/{id}/approvals")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeApproval recordApproval(@PathVariable Long id, @RequestBody ChangeApproval body) {
        return service.recordApproval(id, body);
    }

    // ── Tasks ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/tasks")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeTask> tasks(@PathVariable Long id) { return service.tasks(id); }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeTask addTask(@PathVariable Long id, @RequestBody ChangeTask body) { return service.addTask(id, body); }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    public ChangeTask updateTask(@PathVariable Long taskId, @RequestBody ChangeTask body) { return service.updateTask(taskId, body); }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId) { service.deleteTask(taskId); }

    // ── Links (cross-module integration) ────────────────────────────────────

    @GetMapping("/{id}/links")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeLink> links(@PathVariable Long id) { return service.links(id); }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeLink addLink(@PathVariable Long id, @RequestBody ChangeLink body) { return service.addLink(id, body); }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable Long linkId) { service.deleteLink(linkId); }

    /** Inverse lookup — used by other module pages to render their "Linked Changes" panel. */
    @GetMapping("/links/by-entity")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "List all changes linked to a given (entityType, entityId) pair")
    public List<Map<String, Object>> linksForEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        return service.linksForEntity(entityType, entityId);
    }

    // ── Comments ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeComment> comments(@PathVariable Long id) { return service.comments(id); }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeComment addComment(@PathVariable Long id, @RequestBody ChangeComment body) { return service.addComment(id, body); }

    // ── Analytics ───────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public Map<String, Object> analytics() { return service.analytics(); }

    // ── CH-17: authorisation matrix ──────────────────────────────────────────

    @GetMapping("/authorisation-matrix")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "The tenant's (change type x risk) authorisation matrix — the gate + quorum per cell.")
    public List<ChangeAuthorisationMatrix> authorisationMatrix() { return service.authorisationMatrix(); }

    @GetMapping("/{id}/authorisation")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Resolve the authorisation required for a specific change and whether it is satisfied.")
    public Map<String, Object> authorisation(@PathVariable Long id) { return service.resolveAuthorisation(id); }

    // ── CH-17: freeze windows + change calendar + conflicts ───────────────────

    @GetMapping("/freeze-windows")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeFreezeWindow> freezeWindows() { return service.freezeWindows(); }

    @PostMapping("/freeze-windows")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeFreezeWindow createFreezeWindow(@RequestBody ChangeFreezeWindow body) {
        return service.createFreezeWindow(body);
    }

    @DeleteMapping("/freeze-windows/{id}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFreezeWindow(@PathVariable Long id) { service.deleteFreezeWindow(id); }

    @GetMapping("/calendar")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Scheduled changes + active freeze windows intersecting [from,to].")
    public Map<String, Object> calendar(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDateTime f = parseDt(from, java.time.LocalDateTime.now().minusDays(7));
        java.time.LocalDateTime t = parseDt(to, f.plusDays(120));
        return service.calendar(f, t);
    }

    @GetMapping("/{id}/conflicts")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Other live changes whose scheduled window overlaps this change's.")
    public List<Map<String, Object>> conflicts(@PathVariable Long id) { return service.conflicts(id); }

    /** Accept either an ISO date-time ("2026-12-25T09:00:00") or a plain date ("2026-12-25"). */
    private static java.time.LocalDateTime parseDt(String s, java.time.LocalDateTime dflt) {
        if (s == null || s.isBlank()) return dflt;
        try { return java.time.LocalDateTime.parse(s); }
        catch (Exception e) { return java.time.LocalDate.parse(s).atStartOfDay(); }
    }
}
