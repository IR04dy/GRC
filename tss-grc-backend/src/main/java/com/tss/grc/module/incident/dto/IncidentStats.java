package com.tss.grc.module.incident.dto;

public record IncidentStats(
    long total,
    long open,
    long investigating,
    long resolved,
    long critical,
    long high,
    double avgDays
) {}
