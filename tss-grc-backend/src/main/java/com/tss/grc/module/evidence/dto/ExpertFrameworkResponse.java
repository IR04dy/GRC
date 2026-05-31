package com.tss.grc.module.evidence.dto;

import java.util.List;

/**
 * A framework in the GRC Expert / SME catalog, classified into a family
 * (NCA | NDI | PDPL | BCM | OTHER) with control/sub-control counts and an
 * org-level coverage rollup across its sub-controls.
 */
public record ExpertFrameworkResponse(
    Long id,
    String code,
    String family,
    String nameEn,
    String nameAr,
    String version,
    String issuer,
    String descriptionEn,
    int controlCount,
    int subControlCount,
    int coveredCount,
    int partialCount,
    int gapCount,
    List<ExpertControlResponse> controls
) {}
