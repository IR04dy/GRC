package com.tss.grc.module.sust.settings.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Sustainability Settings REST endpoints —
 * {@code /api/sustainability/settings/*}.
 *
 * <pre>
 *   GET    /                lazy-creates default row if missing; returns full settings
 *   PUT    /                update any subset of thresholds (partial-update semantics)
 *   POST   /reset           reset all thresholds to V65 schema defaults
 * </pre>
 */
@RestController
@RequestMapping("/api/sustainability/settings")
@RequiredArgsConstructor
@RequireModule("ESG")
@Tag(name = "Sustainability — Settings")
public class OrganisationSettingsController {

    private final OrganisationSettingsService service;

    @GetMapping
    @PreAuthorize("hasPermission('SUST','READ')")
    public OrganisationSettings get() { return service.getOrCreate(); }

    @PutMapping
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public OrganisationSettings update(@RequestBody OrganisationSettings req) {
        return service.update(req);
    }

    @PostMapping("/reset")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public OrganisationSettings reset() { return service.resetToDefaults(); }
}
