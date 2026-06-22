package com.tss.grc.module.ndi.cycle.controller;

import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
import com.tss.grc.module.ndi.cycle.service.NdiAssessmentCycleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SDAIA NDI Assessment Cycle REST endpoints —
 * {@code /api/ndi/cycles/*}.
 *
 * <pre>
 *   GET    /                  list (filter by status)
 *   GET    /aggregate         counts per status — dashboard widget
 *   GET    /:id               fetch one
 *   POST   /                  create (defaults: DRAFT)
 *   PUT    /:id               update (refused if terminal)
 *   POST   /:id/clone         body { "newYear": 2026 } — clones into new DRAFT
 *   POST   /:id/transition    body { "to": "PLANNING" } — forward step
 *   POST   /:id/archive       any → ARCHIVED (terminal)
 *   DELETE /:id               soft-delete
 *
 *   The 11 spec-mentioned cycle buttons (start/freeze/reopen/submit-review/
 *   approve/submit/archive/etc.) all funnel through /transition with the
 *   appropriate target status, keeping the controller surface narrow.
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/cycles")
@RequiredArgsConstructor
@Tag(name = "NDI — Assessment Cycles")
public class NdiAssessmentCycleController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiAssessmentCycleService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiAssessmentCycle> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (status != null) return service.listByStatus(status, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize(READ)
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiAssessmentCycle get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiAssessmentCycle> create(@RequestBody NdiAssessmentCycle req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiAssessmentCycle update(@PathVariable Long id, @RequestBody NdiAssessmentCycle req) {
        return service.update(id, req);
    }

    /** Body: {@code { "to": "PLANNING" } } — see service for the allowed forward steps. */
    @PostMapping("/{id}/transition")
    @PreAuthorize(WRITE)
    public NdiAssessmentCycle transition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.transition(id, body.get("to"));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(APPROVE)
    public NdiAssessmentCycle archive(@PathVariable Long id) { return service.transition(id, "ARCHIVED"); }

    /** Body: {@code { "newYear": 2026 } }. */
    @PostMapping("/{id}/clone")
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiAssessmentCycle> cloneCycle(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        Integer y = body == null ? null : body.get("newYear");
        return ResponseEntity.status(201).body(
            service.clone(id, y == null ? null : y.shortValue()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
