package com.tss.grc.module.dashboard.dto;

public record DashboardStats(
    long totalRisks,
    long criticalRisks,
    long highRisks,
    long mediumRisks,
    long lowRisks,
    long openRisks,
    long closedRisks,
    long totalUsers,
    long activeUsers,
    // Aliases used by the dashboard UI
    long openCriticalRisks,
    long openHighRisks,
    // Sprint-2 placeholders (0 until those modules are implemented)
    long openIncidents,
    long complianceScore,
    long pendingTasks,
    // Overdue audit findings (due_date past, not closed) — real, replaces the
    // mock-data count the dashboard's "Overdue items" tile previously used.
    long overdueItems
) {}
