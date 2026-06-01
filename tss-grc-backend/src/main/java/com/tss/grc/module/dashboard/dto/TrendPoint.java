package com.tss.grc.module.dashboard.dto;

/** One point in a KPI trend series — a week label and the cumulative value at that week. */
public record TrendPoint(String label, long value) {}
