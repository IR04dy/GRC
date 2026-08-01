package com.tss.grc.module.ndi.dashboard.controller;

import com.tss.grc.module.ndi.dashboard.service.NdiDashboardService;
import com.tss.grc.module.ndi.dashboard.service.NdiDashboardService.DashboardSnapshot;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ndi/dashboard")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
@Tag(name = "NDI — Dashboard")
public class NdiDashboardController {

    private final NdiDashboardService service;

    @GetMapping
    @PreAuthorize("hasPermission('NDI','READ')")
    public DashboardSnapshot snapshot(@RequestParam Long cycleId) {
        return service.snapshot(cycleId);
    }
}
