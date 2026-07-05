package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.BrandMonitoring;
import com.tss.grc.module.socops.service.BrandMonitoringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/socops/brand-monitoring")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Brand / Dark Web Monitoring")
public class BrandMonitoringController {

    private final BrandMonitoringService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','BRAND_MONITORING','PLATFORM_ADMIN','AUDITOR')")
    public Page<BrandMonitoring> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','BRAND_MONITORING','PLATFORM_ADMIN','AUDITOR')")
    public BrandMonitoring get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('BRAND_INGEST','PLATFORM_ADMIN','CISO')")
    public BrandMonitoring ingest(@RequestBody BrandMonitoring body) { return svc.ingest(body); }

    @PostMapping("/{id}/request-takedown")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public BrandMonitoring requestTakedown(@PathVariable Long id) { return svc.requestTakedown(id); }

    @PostMapping("/{id}/complete-takedown")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public BrandMonitoring completeTakedown(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return svc.completeTakedown(id, body.get("notes"));
    }
}
