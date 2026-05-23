package com.tss.grc.module.governance.threelines.dto;

import java.util.List;

/**
 * Aggregate counts for the 3LoD dashboard — one {@link BandCounts} row per
 * IIA band (LINE_1, LINE_2, LINE_3, BOARD).
 *
 * <p>The frontend renders these counts in four columns; each column also
 * carries the localised band label and rationale so the bilingual UI does
 * not have to maintain its own copy of the IIA model.
 */
public record ThreeLinesOverviewDto(
        List<BandCounts> bands,
        long totalRisksOpen,
        long totalFindingsOpen,
        long totalCapaOpen,
        long totalPoliciesPendingApproval,
        long totalOverdue
) {

    public record BandCounts(
            String lodBand,                  // LINE_1 / LINE_2 / LINE_3 / BOARD
            String titleEn,
            String titleAr,
            String subtitleEn,               // Saudi-native column subtitle
            String subtitleAr,
            long risksOpen,
            long findingsOpen,
            long capaOpen,
            long policiesPendingApproval,
            long dsrCasesOpen,
            long overdueItems,
            long approvalsPending,
            List<String> roleNames           // roles mapped to this band for the tenant
    ) {}
}
