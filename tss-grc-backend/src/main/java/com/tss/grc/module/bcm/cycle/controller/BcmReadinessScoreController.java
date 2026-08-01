package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.service.BcmReadinessScoreService;
import com.tss.grc.module.bcm.cycle.service.BcmReadinessScoreService.CycleScore;
import com.tss.grc.module.bcm.cycle.service.BcmReadinessScoreService.ScoreBreakdown;
import com.tss.grc.module.bcm.cycle.service.BcmReadinessScoreService.TenantScore;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the Continuity Readiness Score.
 * <pre>
 *   GET /api/v1/bcm/readiness-score/bia/{biaId}        single-BIA breakdown
 *   GET /api/v1/bcm/readiness-score/cycle/{cycleId}    cycle aggregate + per-BIA list
 *   GET /api/v1/bcm/readiness-score/leaderboard       tenant-wide leaderboard
 *   GET /api/v1/bcm/readiness-score/tenant             tenant headline score + distribution
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/readiness-score")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM Readiness Score")
public class BcmReadinessScoreController {

    private final BcmReadinessScoreService service;

    @GetMapping("/bia/{biaId}")
    @PreAuthorize("isAuthenticated()")
    public ScoreBreakdown forBia(@PathVariable Long biaId) {
        return service.scoreForBia(biaId);
    }

    @GetMapping("/cycle/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public CycleScore forCycle(@PathVariable Long cycleId) {
        return service.scoreForCycle(cycleId);
    }

    @GetMapping("/leaderboard")
    @PreAuthorize("isAuthenticated()")
    public List<ScoreBreakdown> leaderboard() {
        return service.leaderboard();
    }

    @GetMapping("/tenant")
    @PreAuthorize("isAuthenticated()")
    public TenantScore tenant() {
        return service.tenantOverall();
    }
}
