package com.tss.grc.module.ndi.opex.controller;

import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import com.tss.grc.module.ndi.opex.service.NdiOperationalExcellenceKpiService;
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
 * NDI Operational Excellence KPI REST endpoints —
 * {@code /api/ndi/opex/*}.
 *
 * <pre>
 *   GET    /?cycleId=&status=&category=     list within a cycle
 *   GET    /aggregate?cycleId=              per-cycle rollup + opex score
 *   GET    /:id                             fetch one
 *   POST   /                                create new KPI
 *   PUT    /:id                             update (recomputes status)
 *   POST   /:id/update-actual               body { actual, asOf? } — record new reading
 *   DELETE /:id                             soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/opex")
@RequiredArgsConstructor
@Tag(name = "NDI — Operational Excellence KPIs")
public class NdiOperationalExcellenceKpiController {

    private final NdiOperationalExcellenceKpiService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NdiOperationalExcellenceKpi> list(
            @RequestParam Long cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        var p = PageRequest.of(page, size);
        if (status != null)   return service.listByCycleAndStatus(cycleId, status, p);
        if (category != null) return service.listByCycleAndCategory(cycleId, category, p);
        return service.listByCycle(cycleId, p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> aggregate(@RequestParam Long cycleId) {
        return service.aggregateForCycle(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiOperationalExcellenceKpi get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiOperationalExcellenceKpi> create(@RequestBody NdiOperationalExcellenceKpi req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiOperationalExcellenceKpi update(@PathVariable Long id, @RequestBody NdiOperationalExcellenceKpi req) {
        return service.update(id, req);
    }

    /** Body: {@code { "actual": 67.5, "asOf": "2026-03-31" }}. */
    @PostMapping("/{id}/update-actual")
    @PreAuthorize("isAuthenticated()")
    public NdiOperationalExcellenceKpi updateActual(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object actualVal = body == null ? null : body.get("actual");
        if (actualVal == null) throw new IllegalArgumentException("actual required");
        BigDecimal actual = new BigDecimal(actualVal.toString());
        Object asOfVal = body.get("asOf");
        LocalDate asOf = asOfVal == null ? null : LocalDate.parse(asOfVal.toString());
        return service.updateActual(id, actual, asOf);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
