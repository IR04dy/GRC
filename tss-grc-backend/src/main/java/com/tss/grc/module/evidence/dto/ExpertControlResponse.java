package com.tss.grc.module.evidence.dto;

import java.util.List;

/** A control (subdomain / clause) with its sub-controls and a coverage rollup, for the GRC Expert catalog. */
public record ExpertControlResponse(
    Long id,
    String code,
    String nameEn,
    String nameAr,
    int subControlCount,
    int coveredCount,
    int partialCount,
    int gapCount,
    List<ExpertSubControlResponse> subControls
) {}
