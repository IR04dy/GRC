package com.tss.grc.module.compliance.controller;

import com.tss.grc.module.compliance.service.RegulatoryHorizonService;
import com.tss.grc.module.compliance.service.RegulatoryHorizonService.Horizon;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saudi regulatory horizon scanner — Sprint-8 Slice 8-B.
 *
 * <pre>
 *   GET /api/v1/compliance/regulatory-horizon
 * </pre>
 *
 * <p>Returns a tenant-wide snapshot of every not-yet-closed regulatory
 * change with a {@code effectiveDate} bucketed PAST_DUE / IMMINENT (≤30d) /
 * NEAR_TERM (31-90d) / QUARTER (91-180d) / HORIZON (>180d), with
 * authority + impact-level distributions for executive dashboards.
 */
@RestController
@RequestMapping("/api/v1/compliance/regulatory-horizon")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
@Tag(name = "Compliance — Regulatory Horizon")
public class RegulatoryHorizonController {

    private final RegulatoryHorizonService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Horizon scan() {
        return service.scan();
    }
}
