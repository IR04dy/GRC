package com.tss.grc.module.sust.ghg.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.sust.ghg.dto.GhgCreateRequest;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GHG Emission Inventory REST endpoints — {@code /api/sustainability/ghg/*}.
 *
 * <p>Routes mirror the spec exactly:
 * <pre>
 *   GET    /              list (filterable by year, scope)
 *   GET    /aggregate     per-year scope totals + Rule-1 partial flag
 *   GET    /yoy           year-over-year delta per scope
 *   POST   /              create
 *   PUT    /:id           update
 *   DELETE /:id           soft-delete
 *   POST   /:id/submit    DRAFT     → SUBMITTED
 *   POST   /:id/verify    SUBMITTED → VERIFIED   (Rule 2: requires evidence)
 *   POST   /:id/approve   VERIFIED  → APPROVED
 *   POST   /:id/archive   *         → ARCHIVED
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/ghg")
@RequiredArgsConstructor
@RequireModule("ESG")
@Tag(name = "Sustainability — GHG Emission Inventory")
public class GhgEmissionRecordController {

    private final GhgEmissionRecordService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<GhgEmissionRecord> list(
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) Short scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (year  != null) return service.listByYear(year, p);
        if (scope != null) return service.listByScope(scope, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> aggregate(@RequestParam(defaultValue = "2025") Short year) {
        return service.aggregate(year);
    }

    @GetMapping("/yoy")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> yoy(@RequestParam(defaultValue = "2025") Short year) {
        return service.yoy(year);
    }

    /** S-J — multi-year emissions trajectory (per-year scope totals) for the trend chart. */
    @GetMapping("/trajectory")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> trajectory(@RequestParam Short fromYear, @RequestParam Short toYear) {
        return service.trajectory(fromYear, toYear);
    }

    /** V295 — Scope-3 split across the GHG Protocol's 15 value-chain categories. */
    @GetMapping("/scope3-breakdown")
    @PreAuthorize("hasPermission('SUST','READ')")
    public com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService.Scope3Breakdown scope3Breakdown(
            @RequestParam(defaultValue = "2026") Short year) {
        return service.scope3Breakdown(year);
    }

    /** S-J — bulk CSV import (T-06). Raw CSV body; returns per-row created/failed summary. */
    @PostMapping(value = "/import", consumes = { "text/csv", "text/plain" })
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public Map<String, Object> importCsv(@RequestBody String csv) {
        return service.importCsv(csv);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public GhgEmissionRecord get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<GhgEmissionRecord> create(@Valid @RequestBody GhgCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public GhgEmissionRecord update(@PathVariable Long id, @RequestBody GhgEmissionRecord req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public GhgEmissionRecord submit(@PathVariable Long id) { return service.transition(id, "SUBMITTED"); }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public GhgEmissionRecord verify(@PathVariable Long id) { return service.verify(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public GhgEmissionRecord approve(@PathVariable Long id) { return service.transition(id, "APPROVED"); }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public GhgEmissionRecord archive(@PathVariable Long id) { return service.transition(id, "ARCHIVED"); }

    /** S-J — reopen an APPROVED (edit-locked) record for editing; forces re-verification. */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public GhgEmissionRecord reopen(@PathVariable Long id) { return service.reopen(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
