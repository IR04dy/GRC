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

/**
 * Dashboard read endpoints. Each method requires the underlying module's READ
 * permission so dashboard widgets respect the same RBAC as the source data.
 * The aggregate {@code /stats} and {@code /heatmap} endpoints accept any of
 * the relevant module READs (defense-in-depth; the underlying service still
 * filters by what the user can see).
 *
 * Data is always tenant-scoped via {@link TenantContext} regardless of role.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('RISK_READ','INCIDENT_READ','COMPLIANCE_READ','CYBER_READ')")
    public DashboardStats stats() {
        return dashboardService.getStats(TenantContext.requireOrganisationId());
    }

    @GetMapping("/heatmap")
    @PreAuthorize("hasPermission('RISK','READ')")
    public HeatmapData heatmap() {
        return dashboardService.getHeatmap(TenantContext.requireOrganisationId());
    }

    @GetMapping("/risk-by-category")
    @PreAuthorize("hasPermission('RISK','READ')")
    public ChartData riskByCategory() {
        return dashboardService.getRiskByCategory(TenantContext.requireOrganisationId());
    }

    /** Real weekly KPI trend (metric = risks | critical_risks | high_risks | incidents
     *  | findings | dsr | breaches | vendors | bcm_exercises). */
    @GetMapping("/kpi-trend")
    @PreAuthorize("hasAnyAuthority('RISK_READ','INCIDENT_READ','COMPLIANCE_READ','CYBER_READ','PRIVACY_READ','SCM_READ','BCM_READ')")
    public List<com.tss.grc.module.dashboard.dto.TrendPoint> kpiTrend(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "risks") String metric,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "12") int weeks) {
        return dashboardService.getKpiTrend(TenantContext.requireOrganisationId(), metric, weeks);
    }

    @GetMapping("/recent-risks")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<RiskResponse> recentRisks() {
        return dashboardService.getRecentRisks(TenantContext.requireOrganisationId());
    }

    /** Stub — Incident module is Sprint 2. Returns empty list. */
    @GetMapping("/recent-incidents")
    @PreAuthorize("hasPermission('INCIDENT','READ')")
    public List<IncidentSummary> recentIncidents() {
        return List.of();
    }

    /** Stub — Compliance module is Sprint 2. Returns empty chart data. */
    @GetMapping("/compliance-by-framework")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public ChartData complianceByFramework() {
        return new ChartData(List.of(), List.of());
    }

    /** Personal task list — every authenticated user has tasks regardless of module RBAC. */
    @GetMapping("/my-tasks")
    @PreAuthorize("isAuthenticated()")
    public List<TaskSummary> myTasks() {
        return List.of();
    }
}
