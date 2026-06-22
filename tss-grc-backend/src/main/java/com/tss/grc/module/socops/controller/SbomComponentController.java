package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.SbomComponent;
import com.tss.grc.module.socops.service.SbomComponentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/socops/sbom")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Software Bill of Materials")
public class SbomComponentController {

    private final SbomComponentService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public Page<SbomComponent> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "100") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN','SBOM_INGEST')")
    public SbomComponent save(@RequestBody SbomComponent body) { return svc.save(body); }
}
