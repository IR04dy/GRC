package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.SeasonalOverlay;
import com.tss.grc.module.socops.service.SeasonalOverlayService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/socops/seasonal-overlays")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Saudi Seasonal Risk Overlays")
public class SeasonalOverlayController {

    private final SeasonalOverlayService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR')")
    public List<SeasonalOverlay> list() { return svc.list(); }

    @GetMapping("/active-now")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN','SECURITY_ANALYST','RISK_MANAGER')")
    public List<SeasonalOverlay> activeNow() { return svc.activeNow(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR')")
    public SeasonalOverlay get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    public SeasonalOverlay save(@RequestBody SeasonalOverlay body) { return svc.save(body); }
}
