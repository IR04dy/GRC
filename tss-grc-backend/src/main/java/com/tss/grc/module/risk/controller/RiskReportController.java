package com.tss.grc.module.risk.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.risk.dto.RiskReportDtos.AppetiteBreachRow;
import com.tss.grc.module.risk.dto.RiskReportDtos.AuditPackage;
import com.tss.grc.module.risk.dto.RiskReportDtos.RegisterStats;
import com.tss.grc.module.risk.dto.RiskReportDtos.TopRiskRow;
import com.tss.grc.module.risk.service.RiskReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ERM epic E-K — risk reporting + audit evidence package.
 *
 * <pre>
 *   GET /api/v1/risk/reports/top-risks?limit=10   top residual risks (board / committee)
 *   GET /api/v1/risk/reports/appetite-breaches     appetite-breach report (?openOnly=true)
 *   GET /api/v1/risk/reports/register-stats        live register posture
 *   GET /api/v1/risk/reports/audit-package         single auditor-pullable evidence package
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/risk/reports")
@RequiredArgsConstructor
@RequireModule("RISK")
@Tag(name = "Risk Management — Reports & Audit Package")
@SecurityRequirement(name = "bearerAuth")
public class RiskReportController {

    private final RiskReportService service;

    @GetMapping("/top-risks")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "Top residual risks for the board / committee report")
    public List<TopRiskRow> topRisks(@RequestParam(defaultValue = "10") int limit) {
        return service.topRisks(limit);
    }

    @GetMapping("/appetite-breaches")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "Appetite-breach report with statement context (openOnly filters to OPEN breaches)")
    public List<AppetiteBreachRow> appetiteBreaches(@RequestParam(defaultValue = "false") boolean openOnly) {
        return service.appetiteBreaches(openOnly);
    }

    @GetMapping("/register-stats")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "Live register posture — counts by level + status, overdue reviews, acceptances, open breaches")
    public RegisterStats registerStats() {
        return service.registerStats();
    }

    @GetMapping("/audit-package")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "A single auditor-pullable risk evidence package (posture + top risks + open breaches)")
    public AuditPackage auditPackage() {
        return service.auditPackage();
    }
}
