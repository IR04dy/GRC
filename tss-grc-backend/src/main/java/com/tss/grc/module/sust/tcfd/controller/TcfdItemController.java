package com.tss.grc.module.sust.tcfd.controller;

import com.tss.grc.module.sust.tcfd.dto.TcfdItemCreateRequest;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.service.TcfdItemService;
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
 * TCFD alignment REST endpoints — {@code /api/sustainability/tcfd/*}.
 */
@RestController
@RequestMapping("/api/sustainability/tcfd")
@RequiredArgsConstructor
@Tag(name = "Sustainability — TCFD Alignment")
public class TcfdItemController {

    private final TcfdItemService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<TcfdItem> list(
            @RequestParam(defaultValue = "2025") Short year,
            @RequestParam(required = false) String pillar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (pillar != null) return service.listByYearAndPillar(year, pillar, p);
        return service.listByYear(year, p);
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> readiness(@RequestParam(defaultValue = "2025") Short year) {
        return service.readiness(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public TcfdItem get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<TcfdItem> create(@Valid @RequestBody TcfdItemCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem update(@PathVariable Long id, @RequestBody TcfdItem req) {
        return service.update(id, req);
    }

    /**
     * S-K — approve a TCFD item. The approver is the authenticated principal
     * (no request body), enforcing non-forgeable attribution + separation of
     * duties in the service. An approved item is content-locked.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem approve(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails me) {
        return service.approve(id, me);
    }

    @PostMapping("/{id}/mark-disclosed")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem markDisclosed(@PathVariable Long id) { return service.transition(id, "DISCLOSED"); }

    @PostMapping("/{id}/mark-partial")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem markPartial(@PathVariable Long id) { return service.transition(id, "PARTIAL"); }

    @PostMapping("/{id}/mark-not-disclosed")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem markNotDisclosed(@PathVariable Long id) { return service.transition(id, "NOT_DISCLOSED"); }

    @PostMapping("/{id}/mark-not-applicable")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem markNotApplicable(@PathVariable Long id) { return service.transition(id, "NOT_APPLICABLE"); }

    @PostMapping("/{id}/mark-under-review")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem markUnderReview(@PathVariable Long id) { return service.transition(id, "UNDER_REVIEW"); }

    @PostMapping("/{id}/attach-evidence")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem attachEvidence(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.attachEvidence(id, body.get("evidenceRef"), body.get("owner"));
    }

    @PostMapping("/{id}/record-gap")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem recordGap(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.recordGap(id, body.get("gapEn"), body.get("gapAr"));
    }

    /** S-F — reverse visibility: TCFD items linked to a given climate risk/opportunity. */
    @GetMapping("/by-climate-risk/{climateRiskId}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public List<TcfdItem> byClimateRisk(@PathVariable Long climateRiskId) {
        return service.listByClimateRisk(climateRiskId);
    }

    /** Body: {@code { "climateRiskId": 123 }}. */
    @PostMapping("/{id}/link-climate-risk")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public TcfdItem linkClimateRisk(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object v = body.get("climateRiskId");
        if (!(v instanceof Number n))
            throw new IllegalArgumentException("climateRiskId required (number).");
        return service.linkClimateRisk(id, n.longValue());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
