package com.tss.grc.module.sust.climate.controller;

import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.service.ClimateRiskOpportunityService;
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
 * Climate Risk &amp; Opportunity REST endpoints.
 *
 * <p>Routes mirror the spec — {@code /api/sustainability/climate-risks/*}
 * — including the lifecycle transitions ({@code /assess}, {@code /treat},
 * {@code /close}, {@code /retire}) and the Rule-8 ERM-link endpoint
 * ({@code POST /{id}/link-erm}).
 */
@RestController
@RequestMapping("/api/sustainability/climate-risks")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Climate Risks & Opportunities")
public class ClimateRiskOpportunityController {

    private final ClimateRiskOpportunityService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<ClimateRiskOpportunity> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        if (kind != null)   return service.listByKind(kind, p);
        if (status != null) return service.listByStatus(status, p);
        return service.list(p);
    }

    /** Top-5 highest-scoring (not RETIRED) — dashboard widget. */
    @GetMapping("/top")
    @PreAuthorize("isAuthenticated()")
    public List<ClimateRiskOpportunity> top() {
        return service.topByRisk();
    }

    /** Whole-org rollup — drives the dashboard composite's climate-treated component. */
    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public java.util.Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClimateRiskOpportunity> create(@RequestBody ClimateRiskOpportunity req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity update(@PathVariable Long id, @RequestBody ClimateRiskOpportunity req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/assess")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity assess(@PathVariable Long id) { return service.transition(id, "ASSESSED"); }

    @PostMapping("/{id}/treat")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity treat(@PathVariable Long id) { return service.transition(id, "TREATED"); }

    @PostMapping("/{id}/close")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity close(@PathVariable Long id) { return service.transition(id, "CLOSED"); }

    @PostMapping("/{id}/retire")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity retire(@PathVariable Long id) { return service.transition(id, "RETIRED"); }

    /** Rule 8 — link the climate item to an existing ERM risk record. */
    @PostMapping("/{id}/link-erm")
    @PreAuthorize("isAuthenticated()")
    public ClimateRiskOpportunity linkErm(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.linkErm(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
