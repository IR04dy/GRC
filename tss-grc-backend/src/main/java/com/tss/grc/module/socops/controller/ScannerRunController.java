package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.ScannerRun;
import com.tss.grc.module.socops.service.ScannerRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Browse vulnerability scanner runs. New runs are created via the ingest
 * endpoint on {@link SocOpsController} ({@code POST /api/v1/socops/scanner-runs});
 * this controller is read-only.
 */
@RestController
@RequestMapping("/api/v1/socops/scanner-runs")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Scanner Runs")
public class ScannerRunController {

    private final ScannerRunService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public Page<ScannerRun> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','PLATFORM_ADMIN','AUDITOR')")
    public ScannerRun get(@PathVariable Long id) { return svc.get(id); }
}
