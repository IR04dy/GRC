package com.tss.grc.module.scm.dto;

import lombok.Data;

@Data
public class ScmDashboardStats {
    private long totalVendors;
    private long criticalVendors;
    private long activeVendors;
    private long pendingOnboarding;
    private long totalDependencies;
    private long highDependencies;
    private long openIncidents;
    private long slaBreaches;
    private long activeContracts;
    private long expiringContracts;
}
