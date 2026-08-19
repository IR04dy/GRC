package com.tss.grc.module.ethics.controller;

import jakarta.validation.Valid;
import com.tss.grc.module.ethics.dto.CaseActionRequest;
import com.tss.grc.module.ethics.entity.CaseAction;
import com.tss.grc.module.ethics.entity.CaseLink;
import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.service.EthicsCaseService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
 * Ethics & Compliance Cases REST surface (whistleblower hotline + investigations).
 *
 * Permission key: COMPLIANCE — these resources are part of the broader
 * compliance program. Reporter PII is sensitive: handlers should ensure only
 * users with COMPLIANCE:WRITE / DELETE see un-redacted records (the entity
 * already supports anonymous reporters).
 */
@RestController
@RequestMapping("/api/v1/ethics/cases")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics & Compliance — Cases (Hotline + Investigations)")
@SecurityRequirement(name = "bearerAuth")
public class EthicsCaseController {

    private final EthicsCaseService service;

    @GetMapping
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<EthicsCase> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(status, category, severity, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public EthicsCase get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public EthicsCase create(@RequestBody EthicsCase body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public EthicsCase update(@PathVariable Long id, @RequestBody EthicsCase body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @Operation(summary = "Move a case through INTAKE → TRIAGE → INVESTIGATION → outcome → CLOSED")
    public EthicsCase transition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        if (target == null) throw new IllegalArgumentException("target is required");
        return service.transition(id, target, body);
    }

    // ── Action timeline ─────────────────────────────────────────────────────

    @GetMapping("/{id}/actions")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<CaseAction> actions(@PathVariable Long id) { return service.actions(id); }

    /**
     * F2-248 — binds {@link CaseActionRequest}, not the entity, so the actor cannot arrive.
     *
     * <p>Like its twin on the change-comment thread, this endpoint sat in
     * {@code MassAssignmentRatchetTest}'s <b>LOW</b> tier — five endpoints of 214 — because the
     * ranking's agent-noun vocabulary does not spell {@code actor}. The service now takes both
     * halves of the attribution from the authenticated principal.
     */
    @PostMapping("/{id}/actions")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CaseAction addAction(@PathVariable Long id, @Valid @RequestBody CaseActionRequest body) {
        return service.addAction(id, body);
    }

    // ── Cross-module links ──────────────────────────────────────────────────

    @GetMapping("/{id}/links")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<CaseLink> links(@PathVariable Long id) { return service.links(id); }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CaseLink addLink(@PathVariable Long id, @RequestBody CaseLink body) {
        return service.addLink(id, body);
    }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable Long linkId) { service.deleteLink(linkId); }

    @GetMapping("/links/by-entity")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "List all ethics cases linked to a given (entityType, entityId)")
    public List<Map<String, Object>> linksForEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        return service.linksForEntity(entityType, entityId);
    }

    // ── Analytics ───────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> analytics() { return service.analytics(); }
}
