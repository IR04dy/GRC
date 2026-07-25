package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmSettings;
import com.tss.grc.module.bcm.cycle.service.BcmSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface for BCM tenant settings.
 * <pre>
 *   GET    /api/v1/bcm/settings          fetch (auto-creates default row if missing)
 *   PATCH  /api/v1/bcm/settings          partial update
 *   POST   /api/v1/bcm/settings/reset    restore to defaults
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm/settings")
@RequiredArgsConstructor
@Tag(name = "BCM Settings")
public class BcmSettingsController {

    private final BcmSettingsService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public BcmSettings get() { return service.getOrInit(); }

    @PatchMapping
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public BcmSettings update(@RequestBody BcmSettings body) { return service.update(body); }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public BcmSettings reset() { return service.reset(); }
}
