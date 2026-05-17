package com.tss.grc.module.sust.evidence.controller;

import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import com.tss.grc.module.sust.evidence.service.SustainabilityEvidenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Sustainability — Evidence Repository")
public class SustainabilityEvidenceController {

    private final SustainabilityEvidenceService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<SustainabilityEvidence> list(
            @RequestParam(required = false) String moduleArea,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        if (moduleArea != null) return service.listByModule(moduleArea, p);
        if (status != null)     return service.listByStatus(status, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SustainabilityEvidence get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SustainabilityEvidence> create(@RequestBody SustainabilityEvidence req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SustainabilityEvidence update(@PathVariable Long id, @RequestBody SustainabilityEvidence req) {
        return service.update(id, req);
    }

    /** Body: {@code { "approvedBy": "Name" }}. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public SustainabilityEvidence approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approvedBy"));
    }

    /** Body: {@code { "reviewerName": "Name", "reason": "..." }}. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public SustainabilityEvidence reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.reject(id, body.get("reviewerName"), body.get("reason"));
    }

    /** Supersede an APPROVED row with a fresh upload. */
    @PostMapping("/{id}/supersede")
    @PreAuthorize("isAuthenticated()")
    public SustainabilityEvidence supersede(@PathVariable Long id, @RequestBody SustainabilityEvidence newReq) {
        return service.supersede(id, newReq);
    }

    /** Rule 9 — flip APPROVED rows whose valid_to has passed to EXPIRED. */
    @PostMapping("/sweep-expired")
    @PreAuthorize("isAuthenticated()")
    public List<SustainabilityEvidence> sweepExpired() { return service.sweepExpired(); }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
