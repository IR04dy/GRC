package com.tss.grc.module.ndi.plan.controller;

import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlanItem;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService.EnrichedPlanItem;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService.PlanRollup;
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
@Tag(name = "NDI — Improvement Plans")
public class NdiImprovementPlanController {

    private final NdiImprovementPlanService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NdiImprovementPlan> listForCycle(@RequestParam Long cycleId) {
        return service.listForCycle(cycleId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiImprovementPlan get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiImprovementPlan> create(@RequestBody NdiImprovementPlan body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiImprovementPlan update(@PathVariable Long id, @RequestBody NdiImprovementPlan body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public NdiImprovementPlan approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(id, body == null ? null : body.get("approverName"));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public NdiImprovementPlan transition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.transition(id, body.get("to"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recompute-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> recomputeAll() {
        service.recomputeAllForOrg();
        return ResponseEntity.noContent().build();
    }

    // ─── Items ─────────────────────────────────────────────────────

    @GetMapping("/{planId}/items")
    @PreAuthorize("isAuthenticated()")
    public List<EnrichedPlanItem> listItems(@PathVariable Long planId) {
        return service.listItems(planId);
    }

    @PostMapping("/{planId}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiImprovementPlanItem> addItem(@PathVariable Long planId,
                                                          @RequestBody NdiImprovementPlanItem body) {
        return ResponseEntity.ok(service.addItem(planId, body));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiImprovementPlanItem updateItem(@PathVariable Long id, @RequestBody NdiImprovementPlanItem body) {
        return service.updateItem(id, body);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{planId}/reorder")
    @PreAuthorize("isAuthenticated()")
    public List<NdiImprovementPlanItem> reorder(@PathVariable Long planId, @RequestBody Map<String, List<Long>> body) {
        return service.reorder(planId, body.get("orderedItemIds"));
    }

    @GetMapping("/{planId}/rollup")
    @PreAuthorize("isAuthenticated()")
    public PlanRollup rollup(@PathVariable Long planId) {
        return service.rollup(planId);
    }
}
