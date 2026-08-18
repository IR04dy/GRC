package com.tss.grc.module.sust.materiality.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.sust.materiality.dto.MaterialTopicCreateRequest;
import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import com.tss.grc.module.sust.materiality.service.MaterialTopicService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ESG Materiality REST endpoints — {@code /api/sustainability/materiality/*}.
 */
@RestController
@RequestMapping("/api/sustainability/materiality")
@RequiredArgsConstructor
@RequireModule("ESG")
@Tag(name = "Sustainability — ESG Materiality")
public class MaterialTopicController {

    private final MaterialTopicService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<MaterialTopic> list(
            @RequestParam(defaultValue = "2025") Short year,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (category != null) return service.listByYearAndCategory(year, category, p);
        if (status   != null) return service.listByYearAndStatus(year, status, p);
        return service.listByYear(year, p);
    }

    @GetMapping("/generate-matrix")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> generateMatrix(@RequestParam(defaultValue = "2025") Short year) {
        return service.generateMatrix(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public MaterialTopic get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<MaterialTopic> create(@Valid @RequestBody MaterialTopicCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public MaterialTopic update(@PathVariable Long id, @RequestBody MaterialTopic req) {
        return service.update(id, req);
    }

    /**
     * S-Q — score on the double-materiality axes.
     * Body: {@code { "impactMateriality": 4.0, "financialMateriality": 3.5 }}.
     * The legacy keys {@code stakeholderImportance}/{@code businessImpact} are
     * still accepted as a fallback so existing clients keep working.
     */
    @PostMapping("/{id}/score")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public MaterialTopic score(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object impactRaw    = body.get("impactMateriality")    != null ? body.get("impactMateriality")    : body.get("stakeholderImportance");
        Object financialRaw = body.get("financialMateriality") != null ? body.get("financialMateriality") : body.get("businessImpact");
        return service.score(id, asBd(impactRaw), asBd(financialRaw));
    }

    /** F2-236 — no body: the approver is the authenticated principal. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public MaterialTopic approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public MaterialTopic archive(@PathVariable Long id) { return service.archive(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private static BigDecimal asBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }
}
