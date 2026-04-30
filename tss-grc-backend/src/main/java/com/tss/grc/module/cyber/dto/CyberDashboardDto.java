package com.tss.grc.module.cyber.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CyberDashboardDto {

    // Controls
    private int totalControls;
    private int compliantControls;
    private int nonCompliantControls;
    private int partialControls;

    // Vulnerabilities
    private int openVulnerabilities;
    private int criticalVulnerabilities;
    private int overdueVulnerabilities;

    // Incidents
    private int openIncidents;
    private int criticalIncidents;

    // Assets
    private int totalAssets;
    private int criticalAssets;

    // Action Plans
    private int openActionPlans;
    private int overdueActionPlans;

    // Evidence
    private int totalEvidence;
    private int expiredEvidence;

    // Recent items
    private List<Object> recentIncidents;
    private List<Object> recentVulnerabilities;
    private List<Object> recentActionPlans;
}
