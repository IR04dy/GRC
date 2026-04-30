package com.tss.grc.module.dashboard.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.dashboard.dto.ChartData;
import com.tss.grc.module.dashboard.dto.DashboardStats;
import com.tss.grc.module.dashboard.dto.HeatmapData;
import com.tss.grc.module.dashboard.dto.IncidentSummary;
import com.tss.grc.module.dashboard.dto.TaskSummary;
import com.tss.grc.module.dashboard.service.DashboardService;
import com.tss.grc.module.risk.dto.RiskResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public DashboardStats stats() {
        return dashboardService.getStats(TenantContext.getCurrentTenant());
    }

    @GetMapping("/heatmap")
    @PreAuthorize("isAuthenticated()")
    public HeatmapData heatmap() {
        return dashboardService.getHeatmap(TenantContext.getCurrentTenant());
    }

    @GetMapping("/risk-by-category")
    @PreAuthorize("isAuthenticated()")
    public ChartData riskByCategory() {
        return dashboardService.getRiskByCategory(TenantContext.getCurrentTenant());
    }

    @GetMapping("/recent-risks")
    @PreAuthorize("isAuthenticated()")
    public List<RiskResponse> recentRisks() {
        return dashboardService.getRecentRisks(TenantContext.getCurrentTenant());
    }

    /** Stub — Incident module is Sprint 2. Returns empty list. */
    @GetMapping("/recent-incidents")
    @PreAuthorize("isAuthenticated()")
    public List<IncidentSummary> recentIncidents() {
        return List.of();
    }

    /** Stub — Compliance module is Sprint 2. Returns empty chart data. */
    @GetMapping("/compliance-by-framework")
    @PreAuthorize("isAuthenticated()")
    public ChartData complianceByFramework() {
        return new ChartData(List.of(), List.of());
    }

    /** Stub — Workflow/Task module is Sprint 2. Returns empty list. */
    @GetMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public List<TaskSummary> myTasks() {
        return List.of();
    }
}
