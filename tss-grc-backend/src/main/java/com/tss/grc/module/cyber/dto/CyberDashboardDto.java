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
    /** Evidence rows whose expiryDate falls in the next 60 days (regulator-watched window). */
    private int expiringEvidence60d;

    // ── Per-framework compliance scores (% compliant / total) ────────────────
    // 0 means the assessment cycle hasn't opened for that framework yet — the
    // frontend distinguishes "not started" from "0% compliant" by showing a
    // tint instead of a red bar.
    private Integer overallScore;
    private Integer eccScore;
    private Integer csccScore;
    private Integer cccScore;
    private Integer otccScore;

    // Recent items
    private List<Object> recentIncidents;
    private List<Object> recentVulnerabilities;
    private List<Object> recentActionPlans;
}
