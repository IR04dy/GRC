package com.tss.grc.module.sust.ghg.controller;

import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
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
@Tag(name = "Sustainability — GHG Emission Inventory")
public class GhgEmissionRecordController {

    private final GhgEmissionRecordService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<GhgEmissionRecord> list(
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) Short scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        if (year  != null) return service.listByYear(year, p);
        if (scope != null) return service.listByScope(scope, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> aggregate(@RequestParam(defaultValue = "2025") Short year) {
        return service.aggregate(year);
    }

    @GetMapping("/yoy")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> yoy(@RequestParam(defaultValue = "2025") Short year) {
        return service.yoy(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GhgEmissionRecord get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GhgEmissionRecord> create(@RequestBody GhgEmissionRecord req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GhgEmissionRecord update(@PathVariable Long id, @RequestBody GhgEmissionRecord req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public GhgEmissionRecord submit(@PathVariable Long id) { return service.transition(id, "SUBMITTED"); }

    @PostMapping("/{id}/verify")
    @PreAuthorize("isAuthenticated()")
    public GhgEmissionRecord verify(@PathVariable Long id) { return service.verify(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public GhgEmissionRecord approve(@PathVariable Long id) { return service.transition(id, "APPROVED"); }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public GhgEmissionRecord archive(@PathVariable Long id) { return service.transition(id, "ARCHIVED"); }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
