package com.tss.grc.module.sust.metrics.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.sust.metrics.dto.EsgMetricCreateRequest;
import com.tss.grc.module.sust.metrics.entity.EsgMetric;
import com.tss.grc.module.sust.metrics.service.EsgMetricService;
import jakarta.validation.Valid;
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
@RequireModule("ESG")
@Tag(name = "Sustainability — ESG Metrics (E/S/G)")
public class EsgMetricController {

    private final EsgMetricService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<EsgMetric> list(
            @RequestParam(defaultValue = "2025") Short year,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (category != null) return service.listByYearAndCategory(year, category, p);
        return service.listByYear(year, p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> aggregate(@RequestParam(defaultValue = "2025") Short year) {
        return service.aggregate(year);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public EsgMetric get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Map<String, Object> progress(@PathVariable Long id) {
        return service.progress(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<EsgMetric> create(@Valid @RequestBody EsgMetricCreateRequest req) {
        return ResponseEntity.status(201).body(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public EsgMetric update(@PathVariable Long id, @RequestBody EsgMetric req) {
        return service.update(id, req);
    }

    /** Body: {@code { "currentValue": 41.5, "asOf": "2026-06-30" }}. */
    @PostMapping("/{id}/record-reading")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public EsgMetric recordReading(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object v = body.get("currentValue");
        if (!(v instanceof Number n))
            throw new IllegalArgumentException("currentValue required (number).");
        LocalDate asOf = body.get("asOf") instanceof String s && !s.isBlank()
            ? LocalDate.parse(s) : null;
        return service.recordReading(id, BigDecimal.valueOf(n.doubleValue()), asOf);
    }

    /** Full reading time-series for a metric (newest first) — S-R history. */
    @GetMapping("/{id}/readings")
    @PreAuthorize("hasPermission('SUST','READ')")
    public java.util.List<com.tss.grc.module.sust.metrics.entity.EsgMetricReading> readings(@PathVariable Long id) {
        return service.listReadings(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public EsgMetric archive(@PathVariable Long id) { return service.archive(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('SUST','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
