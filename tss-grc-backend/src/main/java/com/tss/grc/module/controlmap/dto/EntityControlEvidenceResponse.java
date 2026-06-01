package com.tss.grc.module.controlmap.dto;

/**
 * One mapped sub-control for an entity, enriched with its recommended evidence
 * and the current org's live evidence-collection coverage — the same data the
 * GRC Expert surface shows, but scoped to a single entity's control map. Lets a
 * detail page (e.g. an AI model) present "for each control you mapped, here is
 * the evidence we recommend and whether it is collected" and raise a request
 * inline.
 */
public record EntityControlEvidenceResponse(
        Long subControlId,
        String subControlCode,
        String subControlNameEn,
        String subControlNameAr,
        String controlCode,
        String frameworkCode,
        String satisfaction,
        String evidenceGuidanceEn,
        String evidenceGuidanceAr,
        int total,
        int approved,
        int pendingReview,
        int draft,
        int rejected,
        int expiringSoon,
        int expired,
        /** COVERED / PARTIAL / GAP / EXPIRED — freshness-aware, mirrors GrcExpertService. */
        String coverage
) {}
