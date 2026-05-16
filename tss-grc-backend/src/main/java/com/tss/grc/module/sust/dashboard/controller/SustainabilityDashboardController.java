package com.tss.grc.module.sust.dashboard.controller;

import com.tss.grc.module.sust.dashboard.service.SustainabilityDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Sustainability Dashboard REST endpoint —
 * {@code /api/sustainability/dashboard}.
 *
 * <p>Single read-only call that composes every slice aggregate +
 * computes the ESG Overall Score. Saves the dashboard 8 round-trips
 * on first paint.
 */
@RestController
@RequestMapping("/api/sustainability/dashboard")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Executive Dashboard")
public class SustainabilityDashboardController {

    private final SustainabilityDashboardService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> dashboard(@RequestParam(defaultValue = "2025") Short year) {
        return service.dashboard(year);
    }
}
