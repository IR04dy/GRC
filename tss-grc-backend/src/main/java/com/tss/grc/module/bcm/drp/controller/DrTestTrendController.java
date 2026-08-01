package com.tss.grc.module.bcm.drp.controller;

import com.tss.grc.module.bcm.drp.service.DrTestTrendService;
import com.tss.grc.module.bcm.drp.service.DrTestTrendService.TrendReport;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disaster Recovery test trending — Sprint-9 Slice 9-B.
 *
 * <pre>
 *   GET /api/v1/bcm/dr-test-trend?quarters=8
 * </pre>
 *
 * <p>Returns a rolling-quarter time-series of DR test outcomes
 * (PASSED / FAILED / PARTIAL / N/A) plus a snapshot of total DRPs,
 * never-tested count, overdue count (>12 months), and the recent-12-month
 * pass rate. Pure aggregator over existing {@code bcm.disaster_recovery_plans}.
 */
@RestController
@RequestMapping("/api/v1/bcm/dr-test-trend")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM DR-Test Trend")
public class DrTestTrendController {

    private final DrTestTrendService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public TrendReport trend(@RequestParam(required = false) Integer quarters) {
        return service.trend(quarters);
    }
}
