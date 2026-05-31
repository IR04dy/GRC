package com.tss.grc.module.evidence.dto;

/**
 * One sub-control in the GRC Expert / SME catalog: its bilingual name, the
 * recommended evidence ({@code evidenceGuidance}) an auditor would accept, and
 * a live rollup of the org's evidence-collection status against it.
 *
 * <p>{@code coverage}: COVERED (>=1 approved item) | PARTIAL (items exist but
 * none approved) | GAP (no evidence collected yet).
 */
public record ExpertSubControlResponse(
    Long id,
    String code,
    String nameEn,
    String nameAr,
    String evidenceGuidance,
    String evidenceGuidanceAr,
    int total,
    int approved,
    int pendingReview,
    int draft,
    int rejected,
    int expiringSoon,
    int expired,
    String coverage
) {}
