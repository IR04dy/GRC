package com.tss.grc.module.compliance.dto;

import java.util.List;
import java.util.Map;

public record ComplianceStats(
    long totalControls,
    long implementedControls,
    long partialControls,
    long plannedControls,
    double overallComplianceRate,
    long openFindings,
    long criticalFindings,
    List<Map<String, Object>> frameworkScores
) {}
