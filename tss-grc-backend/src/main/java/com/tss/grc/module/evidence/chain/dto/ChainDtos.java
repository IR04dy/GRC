package com.tss.grc.module.evidence.chain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public final class ChainDtos {
    private ChainDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemSummary(
            Long evidenceId,
            String externalId,
            String titleEn, String titleAr,
            Integer currentVersionNo,
            Integer chainVersionCount,
            String chainStatus,
            LocalDateTime chainLastVerifiedAt,
            Integer chainFirstBreakVersion
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VersionLink(
            Long versionId,
            Integer versionNo,
            Integer chainPosition,
            String contentHashSha256,
            String chainHashSha256,
            String chainStatus,
            String chainBreakReason,
            LocalDateTime snapshottedAt,
            String fileName,
            Long fileSize,
            String changeSummary
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChainReport(
            Long evidenceId,
            String externalId,
            String titleEn,
            String chainStatus,
            int versionCount,
            int validCount,
            int brokenCount,
            Integer firstBreakVersion,
            LocalDateTime verifiedAt,
            List<VersionLink> versions
    ) {}
}
