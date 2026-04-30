package com.tss.grc.module.evidence.dto;

public record EvidenceDashboardStats(
    long totalEvidence,
    long approved,
    long pendingReview,
    long draft,
    long rejected,
    long expired,
    long openRequests,
    long overdueRequests,
    long pendingReviews,
    long frameworks
) {}
