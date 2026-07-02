package com.tss.grc.module.sust.capa.controller;

import com.tss.grc.module.sust.capa.dto.CorrectiveActionCreateRequest;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import jakarta.validation.Valid;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sustainability Corrective-Action REST endpoints —
 * {@code /api/sustainability/corrective-actions/*}.
 *
 * <pre>
 *   GET    /                       list (filterable by status / sourceType / priority)
 *   GET    /aggregate              KPI rollup (by status / priority / source + overdue)
 *   GET    /for-source             all actions raised against (sourceType, sourceRef)
 *   GET    /:id                    fetch one
 *   POST   /                       create (defaults: OPEN / MEDIUM)
 *   PUT    /:id                    update (refused if status ∈ CLOSED, CANCELLED)
 *   POST   /:id/start              OPEN        → IN_PROGRESS
 *   POST   /:id/complete           IN_PROGRESS → COMPLETED   (Rule B: requires evidence)
 *   POST   /:id/verify             COMPLETED   → VERIFIED    (Rule A: verifier ≠ owner)
 *   POST   /:id/close              VERIFIED    → CLOSED      (terminal)
 *   POST   /:id/cancel             any         → CANCELLED   (terminal)
 *   DELETE /:id                    soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/corrective-actions")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Corrective Actions")
public class CorrectiveActionController {

    private final CorrectiveActionService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<CorrectiveAction> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (status     != null) return service.listByStatus(status, p);
        if (sourceType != null) return service.listBySource(sourceType, p);
        if (priority   != null) return service.listByPriority(priority, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/for-source")
    @PreAuthorize("hasPermission('SUST','READ')")
    public List<CorrectiveAction> forSource(
            @RequestParam String sourceType,
            @RequestParam String sourceRef) {
        return service.listForSource(sourceType, sourceRef);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public CorrectiveAction get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<CorrectiveAction> create(@Valid @RequestBody CorrectiveActionCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CorrectiveAction update(@PathVariable Long id, @RequestBody CorrectiveAction req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CorrectiveAction start(@PathVariable Long id) { return service.transition(id, "IN_PROGRESS"); }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CorrectiveAction complete(@PathVariable Long id) { return service.transition(id, "COMPLETED"); }

    /**
     * Verify a corrective action. The verifier is the authenticated caller —
     * Rule A (verifier ≠ owner) is enforced against the principal, not a
     * client-supplied name, so the four-eyes control cannot be bypassed and
     * the attribution cannot be forged (SEC-ESG-002).
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CorrectiveAction verify(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails me) {
        return service.transition(id, "VERIFIED", me);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CorrectiveAction close(@PathVariable Long id) { return service.transition(id, "CLOSED"); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CorrectiveAction cancel(@PathVariable Long id) { return service.transition(id, "CANCELLED"); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
