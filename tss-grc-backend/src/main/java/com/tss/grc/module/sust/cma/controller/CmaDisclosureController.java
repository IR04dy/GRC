package com.tss.grc.module.sust.cma.controller;

import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.service.CmaDisclosureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CMA Articles 91–94 disclosure REST endpoints —
 * {@code /api/sustainability/cma-disclosures/*}.
 *
 * <pre>
 *   GET    /?year=2025                    list (filter by status)
 *   GET    /readiness?year=2025           year-scoped readiness rollup
 *   GET    /:id                           fetch one
 *   POST   /                              create
 *   PUT    /:id                           update (re-applies Rule 5+6)
 *   POST   /:id/mark-disclosed            → DISCLOSED   (Rule 6 enforced)
 *   POST   /:id/mark-partial              → PARTIAL     (Rule 5+6 enforced)
 *   POST   /:id/mark-not-disclosed        → NOT_DISCLOSED (Rule 5 enforced)
 *   POST   /:id/mark-not-applicable       → NOT_APPLICABLE
 *   POST   /:id/mark-under-review         → UNDER_REVIEW
 *   POST   /:id/attach-evidence           {evidenceRef, owner?}
 *   POST   /:id/record-gap                {gapEn?, gapAr?}
 *   POST   /:id/approve                   {approvedBy}
 *   DELETE /:id                           soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/cma-disclosures")
@RequiredArgsConstructor
@Tag(name = "Sustainability — CMA Disclosures (Articles 91-94)")
public class CmaDisclosureController {

    private final CmaDisclosureService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<CmaDisclosure> list(
            @RequestParam(defaultValue = "2025") Short year,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (status != null) return service.listByYearAndStatus(year, status, p);
        return service.listByYear(year, p);
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> readiness(@RequestParam(defaultValue = "2025") Short year) {
        return service.readiness(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public CmaDisclosure get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<CmaDisclosure> create(@RequestBody CmaDisclosure req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure update(@PathVariable Long id, @RequestBody CmaDisclosure req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/mark-disclosed")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure markDisclosed(@PathVariable Long id) { return service.transition(id, "DISCLOSED"); }

    @PostMapping("/{id}/mark-partial")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure markPartial(@PathVariable Long id) { return service.transition(id, "PARTIAL"); }

    @PostMapping("/{id}/mark-not-disclosed")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure markNotDisclosed(@PathVariable Long id) { return service.transition(id, "NOT_DISCLOSED"); }

    @PostMapping("/{id}/mark-not-applicable")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure markNotApplicable(@PathVariable Long id) { return service.transition(id, "NOT_APPLICABLE"); }

    @PostMapping("/{id}/mark-under-review")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure markUnderReview(@PathVariable Long id) { return service.transition(id, "UNDER_REVIEW"); }

    /** Body: {@code { "evidenceRef": "Annual Report 2025 §3.4", "owner": "Sara Al-Zahrani" } }. */
    @PostMapping("/{id}/attach-evidence")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure attachEvidence(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.attachEvidence(id, body.get("evidenceRef"), body.get("owner"));
    }

    /** Body: {@code { "gapEn": "...", "gapAr": "..." } } — either or both. */
    @PostMapping("/{id}/record-gap")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure recordGap(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.recordGap(id, body.get("gapEn"), body.get("gapAr"));
    }

    /** Body: {@code { "approvedBy": "Name" } }. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public CmaDisclosure approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approvedBy"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
