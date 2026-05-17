package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmRisk;
import com.tss.grc.module.bcm.cycle.entity.ContinuityStrategy;
import com.tss.grc.module.bcm.cycle.service.BcmRiskService;
import com.tss.grc.module.bcm.cycle.service.ContinuityStrategyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for BCM risks + continuity strategies — Sprint BCM-1.
 *
 * <pre>
 *   GET    /api/v1/bcm/risks[?cycleId=&serviceId=&status=]
 *   POST   /api/v1/bcm/risks
 *   PUT    /api/v1/bcm/risks/:id
 *   POST   /api/v1/bcm/risks/:id/transition  { "to": "ASSESSED" }
 *   DELETE /api/v1/bcm/risks/:id
 *
 *   GET    /api/v1/bcm/strategies[?cycleId=&serviceId=&approvalStatus=]
 *   POST   /api/v1/bcm/strategies
 *   PUT    /api/v1/bcm/strategies/:id
 *   POST   /api/v1/bcm/strategies/:id/transition { "to": "APPROVED" }
 *   DELETE /api/v1/bcm/strategies/:id
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM — Risks + Strategies (BCM-1)")
public class BcmRiskController {

    private final BcmRiskService risks;
    private final ContinuityStrategyService strategies;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    public record TransitionBody(String to) {}

    // ─── BCM risks ────────────────────────────────────────────────

    @GetMapping("/risks")
    @PreAuthorize("isAuthenticated()")
    public List<BcmRisk> listRisks(@RequestParam(required = false) Long cycleId,
                                   @RequestParam(required = false) Long serviceId,
                                   @RequestParam(required = false) String status) {
        return risks.list(cycleId, serviceId, status);
    }

    @GetMapping("/risks/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmRisk getRisk(@PathVariable Long id) { return risks.get(id); }

    @PostMapping("/risks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcmRisk> createRisk(@RequestBody BcmRisk body) {
        return ResponseEntity.ok(risks.create(body));
    }

    @PutMapping("/risks/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmRisk updateRisk(@PathVariable Long id, @RequestBody BcmRisk body) {
        return risks.update(id, body);
    }

    @PostMapping("/risks/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public BcmRisk transitionRisk(@PathVariable Long id, @RequestBody TransitionBody body) {
        return risks.transition(id, body.to(), actor());
    }

    @DeleteMapping("/risks/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteRisk(@PathVariable Long id) {
        risks.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Continuity strategies ────────────────────────────────────

    @GetMapping("/strategies")
    @PreAuthorize("isAuthenticated()")
    public List<ContinuityStrategy> listStrategies(@RequestParam(required = false) Long cycleId,
                                                    @RequestParam(required = false) Long serviceId,
                                                    @RequestParam(required = false) String approvalStatus) {
        return strategies.list(cycleId, serviceId, approvalStatus);
    }

    @GetMapping("/strategies/{id}")
    @PreAuthorize("isAuthenticated()")
    public ContinuityStrategy getStrategy(@PathVariable Long id) { return strategies.get(id); }

    @PostMapping("/strategies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContinuityStrategy> createStrategy(@RequestBody ContinuityStrategy body) {
        return ResponseEntity.ok(strategies.create(body));
    }

    @PutMapping("/strategies/{id}")
    @PreAuthorize("isAuthenticated()")
    public ContinuityStrategy updateStrategy(@PathVariable Long id, @RequestBody ContinuityStrategy body) {
        return strategies.update(id, body);
    }

    @PostMapping("/strategies/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public ContinuityStrategy transitionStrategy(@PathVariable Long id, @RequestBody TransitionBody body) {
        return strategies.transitionApproval(id, body.to(), actor());
    }

    @DeleteMapping("/strategies/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteStrategy(@PathVariable Long id) {
        strategies.delete(id);
        return ResponseEntity.noContent().build();
    }
}
