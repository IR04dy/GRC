package com.tss.grc.module.sust.sgi.controller;

import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.service.SgiCommitmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Saudi Green Initiative REST endpoints —
 * {@code /api/sustainability/sgi-commitments/*}.
 *
 * <pre>
 *   GET    /                       list (filter by status / sector)
 *   GET    /aggregate              KPI rollup (tiers, sectors, achieved/retired)
 *   GET    /:id                    fetch one
 *   GET    /:id/progress           computed progress + tier for one commitment
 *   GET    /by-code/:code          fetch by commitment_code (lookup for CapaTab)
 *   POST   /                       create (defaults: ACTIVE)
 *   PUT    /:id                    update (refused if status ∈ ACHIEVED, RETIRED)
 *   POST   /:id/achieve            ACTIVE → ACHIEVED   (Rule A: current vs target)
 *   POST   /:id/retire             ACTIVE → RETIRED    (Rule B: requires reason)
 *   DELETE /:id                    soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/sgi-commitments")
@RequiredArgsConstructor
@Tag(name = "Sustainability — SGI Commitments")
public class SgiCommitmentController {

    private final SgiCommitmentService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<SgiCommitment> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sector,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        if (status != null) return service.listByStatus(status, p);
        if (sector != null) return service.listBySector(sector, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SgiCommitment get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/{id}/progress")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> progress(@PathVariable Long id) {
        return service.progress(service.get(id));
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("isAuthenticated()")
    public SgiCommitment getByCode(@PathVariable String code) { return service.getByCode(code); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SgiCommitment> create(@RequestBody SgiCommitment req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SgiCommitment update(@PathVariable Long id, @RequestBody SgiCommitment req) {
        return service.update(id, req);
    }

    /** Body: {@code { "achievedBy": "Name" }}. Rule A enforced server-side. */
    @PostMapping("/{id}/achieve")
    @PreAuthorize("isAuthenticated()")
    public SgiCommitment achieve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String by = body == null ? null : body.get("achievedBy");
        return service.achieve(id, by);
    }

    /** Body: {@code { "retiredBy": "Name", "reason": "Replaced by …" }}. */
    @PostMapping("/{id}/retire")
    @PreAuthorize("isAuthenticated()")
    public SgiCommitment retire(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.retire(id, body.get("retiredBy"), body.get("reason"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
