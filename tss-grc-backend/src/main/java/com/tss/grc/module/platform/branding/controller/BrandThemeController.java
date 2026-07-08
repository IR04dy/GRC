package com.tss.grc.module.platform.branding.controller;

import com.tss.grc.module.platform.branding.dto.BrandThemeRequest;
import com.tss.grc.module.platform.branding.dto.BrandThemeResponse;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export P2 — per-tenant brand theme surface. Reading is open to any authenticated user
 * in the tenant (the UI and export engines both need it); writing requires PLATFORM:WRITE.
 */
@RestController
@RequestMapping("/api/v1/platform/brand-theme")
@RequiredArgsConstructor
public class BrandThemeController {

    private final BrandThemeService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public BrandThemeResponse get() {
        return service.resolveResponse();
    }

    @PutMapping
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public BrandThemeResponse upsert(@Valid @RequestBody BrandThemeRequest request) {
        return service.upsert(request);
    }
}
