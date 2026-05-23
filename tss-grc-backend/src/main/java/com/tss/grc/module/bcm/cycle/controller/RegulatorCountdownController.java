package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.service.RegulatorCountdownService;
import com.tss.grc.module.bcm.cycle.service.RegulatorCountdownService.CountdownRow;
import com.tss.grc.module.bcm.cycle.service.RegulatorCountdownService.CountdownSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Regulator countdown timers — Slice 7-E (Sprint-7).
 *
 * <pre>
 *   GET /api/v1/bcm/regulator-countdowns                      — tenant-wide active rows
 *   GET /api/v1/bcm/regulator-countdowns/summary              — tenant-wide counts by status
 *   GET /api/v1/bcm/regulator-countdowns/event/{crisisEventId}— one event's timers
 * </pre>
 *
 * <p>Returns Saudi regulator notification countdowns (SAMA Cyber 4h /
 * SAMA BCF 6h / NCA 24h / SDAIA PDPL 72h / NCEMA 24h / CMA 24h / MOH 24h)
 * for active crisis events. Pure read aggregator — no schema changes.
 */
@RestController
@RequestMapping("/api/v1/bcm/regulator-countdowns")
@RequiredArgsConstructor
@Tag(name = "BCM Regulator Countdowns")
public class RegulatorCountdownController {

    private final RegulatorCountdownService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CountdownRow> active() {
        return service.activeCountdowns();
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public CountdownSummary summary() {
        return service.summary();
    }

    @GetMapping("/event/{crisisEventId}")
    @PreAuthorize("isAuthenticated()")
    public List<CountdownRow> forEvent(@PathVariable Long crisisEventId) {
        return service.forCrisisEvent(crisisEventId);
    }
}
