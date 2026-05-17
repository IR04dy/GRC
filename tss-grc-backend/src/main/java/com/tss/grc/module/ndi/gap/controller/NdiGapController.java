package com.tss.grc.module.ndi.gap.controller;

import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.entity.NdiGap;
import com.tss.grc.module.ndi.gap.service.NdiGapService;
import com.tss.grc.module.ndi.gap.service.NdiGapService.GapAggregate;
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
 * NDI Gap + CAPA REST endpoints — {@code /api/ndi/gaps/*}.
 *
 * <pre>
 *   GET    /?cycleId=&status=&severity=          list gaps
 *   GET    /aggregate?cycleId=                   per-cycle rollup
 *   GET    /:id                                  fetch one gap
 *   POST   /                                     create gap
 *   PUT    /:id                                  update gap
 *   DELETE /:id                                  soft-delete (cascades to CAPAs)
 *   POST   /from-source                          upsert gap from source row
 *
 *   GET    /:gapId/capas                         list CAPAs for a gap
 *   POST   /:gapId/capas                         create CAPA
 *   PUT    /capas/:id                            update CAPA
 *   DELETE /capas/:id                            soft-delete CAPA
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/gaps")
@RequiredArgsConstructor
@Tag(name = "NDI — Gaps + Corrective Actions")
public class NdiGapController {

    private final NdiGapService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NdiGap> listGaps(@RequestParam Long cycleId,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String severity,
                                 @RequestParam(defaultValue = "0")   int page,
                                 @RequestParam(defaultValue = "200") int size) {
        return service.listGaps(cycleId, status, severity, PageRequest.of(page, size));
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public GapAggregate aggregate(@RequestParam Long cycleId) { return service.aggregate(cycleId); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiGap getGap(@PathVariable Long id) { return service.getGap(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiGap> createGap(@RequestBody NdiGap body) {
        return ResponseEntity.ok(service.createGap(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiGap updateGap(@PathVariable Long id, @RequestBody NdiGap body) {
        return service.updateGap(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteGap(@PathVariable Long id) {
        service.deleteGap(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/from-source")
    @PreAuthorize("isAuthenticated()")
    public NdiGap upsertFromSource(@RequestBody Map<String, Object> body) {
        return service.upsertFromSource(
            ((Number) body.get("cycleId")).longValue(),
            (String) body.get("sourceType"),
            ((Number) body.get("sourceId")).longValue(),
            (String) body.get("domain"),
            (String) body.get("titleEn"),
            (String) body.get("titleAr"),
            (String) body.get("severity"),
            (String) body.get("ownerName"));
    }

    // ─── CAPAs ─────────────────────────────────────────────────────

    @GetMapping("/{gapId}/capas")
    @PreAuthorize("isAuthenticated()")
    public List<NdiCorrectiveAction> listCapas(@PathVariable Long gapId) {
        return service.listCapas(gapId);
    }

    @PostMapping("/{gapId}/capas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiCorrectiveAction> createCapa(@PathVariable Long gapId,
                                                          @RequestBody NdiCorrectiveAction body) {
        return ResponseEntity.ok(service.createCapa(gapId, body));
    }

    @PutMapping("/capas/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiCorrectiveAction updateCapa(@PathVariable Long id, @RequestBody NdiCorrectiveAction body) {
        return service.updateCapa(id, body);
    }

    @DeleteMapping("/capas/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCapa(@PathVariable Long id) {
        service.deleteCapa(id);
        return ResponseEntity.noContent().build();
    }
}
