package com.tss.grc.module.sust.metrics.controller;

import com.tss.grc.module.sust.metrics.entity.EsgMetric;
import com.tss.grc.module.sust.metrics.service.EsgMetricService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * ESG Metrics REST endpoints — {@code /api/sustainability/metrics/*}.
 */
@RestController
@RequestMapping("/api/sustainability/metrics")
@RequiredArgsConstructor
@Tag(name = "Sustainability — ESG Metrics (E/S/G)")
public class EsgMetricController {

    private final EsgMetricService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<EsgMetric> list(
            @RequestParam(defaultValue = "2025") Short year,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        if (category != null) return service.listByYearAndCategory(year, category, p);
        return service.listByYear(year, p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> aggregate(@RequestParam(defaultValue = "2025") Short year) {
        return service.aggregate(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EsgMetric get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/{id}/progress")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> progress(@PathVariable Long id) {
        return service.progress(service.get(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EsgMetric> create(@RequestBody EsgMetric req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EsgMetric update(@PathVariable Long id, @RequestBody EsgMetric req) {
        return service.update(id, req);
    }

    /** Body: {@code { "currentValue": 41.5, "asOf": "2026-06-30" }}. */
    @PostMapping("/{id}/record-reading")
    @PreAuthorize("isAuthenticated()")
    public EsgMetric recordReading(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object v = body.get("currentValue");
        if (!(v instanceof Number n))
            throw new IllegalArgumentException("currentValue required (number).");
        LocalDate asOf = body.get("asOf") instanceof String s && !s.isBlank()
            ? LocalDate.parse(s) : null;
        return service.recordReading(id, BigDecimal.valueOf(n.doubleValue()), asOf);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public EsgMetric archive(@PathVariable Long id) { return service.archive(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
