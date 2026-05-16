package com.tss.grc.module.sust.materiality.controller;

import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import com.tss.grc.module.sust.materiality.service.MaterialTopicService;
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
@Tag(name = "Sustainability — ESG Materiality")
public class MaterialTopicController {

    private final MaterialTopicService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<MaterialTopic> list(
            @RequestParam(defaultValue = "2025") Short year,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var p = PageRequest.of(page, size);
        if (category != null) return service.listByYearAndCategory(year, category, p);
        if (status   != null) return service.listByYearAndStatus(year, status, p);
        return service.listByYear(year, p);
    }

    @GetMapping("/generate-matrix")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> generateMatrix(@RequestParam(defaultValue = "2025") Short year) {
        return service.generateMatrix(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MaterialTopic get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MaterialTopic> create(@RequestBody MaterialTopic req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MaterialTopic update(@PathVariable Long id, @RequestBody MaterialTopic req) {
        return service.update(id, req);
    }

    /** Body: {@code { "stakeholderImportance": 4.0, "businessImpact": 3.5 }}. */
    @PostMapping("/{id}/score")
    @PreAuthorize("isAuthenticated()")
    public MaterialTopic score(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal s = asBd(body.get("stakeholderImportance"));
        BigDecimal b = asBd(body.get("businessImpact"));
        return service.score(id, s, b);
    }

    /** Body: {@code { "approvedBy": "Name" }}. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public MaterialTopic approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approvedBy"));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public MaterialTopic archive(@PathVariable Long id) { return service.archive(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
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
