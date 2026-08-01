package com.tss.grc.module.sust.evidence.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import com.tss.grc.module.sust.evidence.service.SustainabilityEvidenceService;
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
 * Sustainability Evidence REST endpoints —
 * {@code /api/sustainability/evidence/*}.
 */
@RestController
@RequestMapping("/api/sustainability/evidence")
@RequiredArgsConstructor
@RequireModule("ESG")
@Tag(name = "Sustainability — Evidence Repository")
public class SustainabilityEvidenceController {

    private final SustainabilityEvidenceService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<SustainabilityEvidence> list(
            @RequestParam(required = false) String moduleArea,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (moduleArea != null) return service.listByModule(moduleArea, p);
        if (status != null)     return service.listByStatus(status, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public SustainabilityEvidence get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<SustainabilityEvidence> create(@RequestBody SustainabilityEvidence req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityEvidence update(@PathVariable Long id, @RequestBody SustainabilityEvidence req) {
        return service.update(id, req);
    }

    /**
     * Approve evidence. The approver is the authenticated caller — not a
     * client-supplied name — so approval attribution cannot be forged and
     * the four-eyes control holds (SEC-ESG-002).
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityEvidence approve(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails me) {
        return service.approve(id, me);
    }

    /** Body: {@code { "reason": "..." }} — reviewer is the authenticated caller. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityEvidence reject(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         @AuthenticationPrincipal GrcUserDetails me) {
        return service.reject(id, me, body == null ? null : body.get("reason"));
    }

    /** Supersede an APPROVED row with a fresh upload. */
    @PostMapping("/{id}/supersede")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityEvidence supersede(@PathVariable Long id, @RequestBody SustainabilityEvidence newReq) {
        return service.supersede(id, newReq);
    }

    /** Rule 9 — flip APPROVED rows whose valid_to has passed to EXPIRED. */
    @PostMapping("/sweep-expired")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public List<SustainabilityEvidence> sweepExpired() { return service.sweepExpired(); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
