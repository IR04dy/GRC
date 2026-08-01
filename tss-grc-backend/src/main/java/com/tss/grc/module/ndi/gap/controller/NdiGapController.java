package com.tss.grc.module.ndi.gap.controller;

import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.entity.NdiGap;
import com.tss.grc.module.ndi.gap.service.NdiGapService;
import com.tss.grc.module.ndi.gap.service.NdiGapService.GapAggregate;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Gaps + Corrective Actions")
public class NdiGapController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiGapService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiGap> listGaps(@RequestParam Long cycleId,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String severity,
                                 @RequestParam(defaultValue = "0")   int page,
                                 @RequestParam(defaultValue = "200") int size) {
        return service.listGaps(cycleId, status, severity, PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/aggregate")
    @PreAuthorize(READ)
    public GapAggregate aggregate(@RequestParam Long cycleId) { return service.aggregate(cycleId); }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiGap getGap(@PathVariable Long id) { return service.getGap(id); }

    /** Cross-module: returns the ERM risk row this gap's risk_id points to, or null. */
    @GetMapping("/{id}/linked-risk")
    @PreAuthorize(READ)
    public com.tss.grc.module.ndi.integration.NdiCrossLinkService.LinkedRisk linkedRisk(@PathVariable Long id) {
        return service.getLinkedRisk(id);
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiGap> createGap(@RequestBody NdiGap body) {
        return ResponseEntity.ok(service.createGap(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiGap updateGap(@PathVariable Long id, @RequestBody NdiGap body) {
        return service.updateGap(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> deleteGap(@PathVariable Long id) {
        service.deleteGap(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/from-source")
    @PreAuthorize(WRITE)
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
    @PreAuthorize(READ)
    public List<NdiCorrectiveAction> listCapas(@PathVariable Long gapId) {
        return service.listCapas(gapId);
    }

    @PostMapping("/{gapId}/capas")
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiCorrectiveAction> createCapa(@PathVariable Long gapId,
                                                          @RequestBody NdiCorrectiveAction body) {
        return ResponseEntity.ok(service.createCapa(gapId, body));
    }

    @PutMapping("/capas/{id}")
    @PreAuthorize(WRITE)
    public NdiCorrectiveAction updateCapa(@PathVariable Long id, @RequestBody NdiCorrectiveAction body) {
        return service.updateCapa(id, body);
    }

    @DeleteMapping("/capas/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> deleteCapa(@PathVariable Long id) {
        service.deleteCapa(id);
        return ResponseEntity.noContent().build();
    }
}
