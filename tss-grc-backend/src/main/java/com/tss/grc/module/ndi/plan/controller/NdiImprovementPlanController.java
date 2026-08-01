package com.tss.grc.module.ndi.plan.controller;

import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlanItem;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService.EnrichedPlanItem;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService.PlanRollup;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ndi/plans")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Improvement Plans")
public class NdiImprovementPlanController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiImprovementPlanService service;

    @GetMapping
    @PreAuthorize(READ)
    public List<NdiImprovementPlan> listForCycle(@RequestParam Long cycleId) {
        return service.listForCycle(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiImprovementPlan get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiImprovementPlan> create(@RequestBody NdiImprovementPlan body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiImprovementPlan update(@PathVariable Long id, @RequestBody NdiImprovementPlan body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    public NdiImprovementPlan approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approverName"));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(WRITE)
    public NdiImprovementPlan transition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.transition(id, body.get("to"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recompute-all")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> recomputeAll() {
        service.recomputeAllForOrg();
        return ResponseEntity.noContent().build();
    }

    // ─── Items ─────────────────────────────────────────────────────

    @GetMapping("/{planId}/items")
    @PreAuthorize(READ)
    public List<EnrichedPlanItem> listItems(@PathVariable Long planId) {
        return service.listItems(planId);
    }

    @PostMapping("/{planId}/items")
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiImprovementPlanItem> addItem(@PathVariable Long planId,
                                                          @RequestBody NdiImprovementPlanItem body) {
        return ResponseEntity.ok(service.addItem(planId, body));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize(WRITE)
    public NdiImprovementPlanItem updateItem(@PathVariable Long id, @RequestBody NdiImprovementPlanItem body) {
        return service.updateItem(id, body);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{planId}/reorder")
    @PreAuthorize(WRITE)
    public List<NdiImprovementPlanItem> reorder(@PathVariable Long planId, @RequestBody Map<String, List<Long>> body) {
        return service.reorder(planId, body.get("orderedItemIds"));
    }

    @GetMapping("/{planId}/rollup")
    @PreAuthorize(READ)
    public PlanRollup rollup(@PathVariable Long planId) {
        return service.rollup(planId);
    }
}
