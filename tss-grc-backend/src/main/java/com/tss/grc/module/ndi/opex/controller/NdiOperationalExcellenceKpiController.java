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

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiOperationalExcellenceKpiService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiOperationalExcellenceKpi> list(
            @RequestParam Long cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        if (status != null)   return service.listByCycleAndStatus(cycleId, status, p);
        if (category != null) return service.listByCycleAndCategory(cycleId, category, p);
        return service.listByCycle(cycleId, p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize(READ)
    public Map<String, Object> aggregate(@RequestParam Long cycleId) {
        return service.aggregateForCycle(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiOperationalExcellenceKpi get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiOperationalExcellenceKpi> create(@RequestBody NdiOperationalExcellenceKpi req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiOperationalExcellenceKpi update(@PathVariable Long id, @RequestBody NdiOperationalExcellenceKpi req) {
        return service.update(id, req);
    }

    /** Body: {@code { "actual": 67.5, "asOf": "2026-03-31" }}. */
    @PostMapping("/{id}/update-actual")
    @PreAuthorize(WRITE)
    public NdiOperationalExcellenceKpi updateActual(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object actualVal = body == null ? null : body.get("actual");
        if (actualVal == null) throw new IllegalArgumentException("actual required");
        BigDecimal actual = new BigDecimal(actualVal.toString());
        Object asOfVal = body.get("asOf");
        LocalDate asOf = asOfVal == null ? null : LocalDate.parse(asOfVal.toString());
        return service.updateActual(id, actual, asOf);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
