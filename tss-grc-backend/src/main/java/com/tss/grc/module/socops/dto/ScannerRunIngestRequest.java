package com.tss.grc.module.socops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Vulnerability scanner run-completion payload. Scanner adapters (Tenable
 * connector, Qualys QPS push, Defender for Cloud webhook, Wiz issues API,
 * Prisma Cloud findings) POST this on scan completion; the detailed
 * findings are subsequently bulk-loaded into the cyber.vulnerability tables.
 */
public record ScannerRunIngestRequest(
    @NotBlank String scanner,
    String scanName,
    String scope,
    @NotNull LocalDateTime startedAt,
    LocalDateTime completedAt,
    @NotBlank String status,
    Integer findingsTotal,
    Integer findingsCritical,
    Integer findingsHigh,
    Integer findingsMedium,
    Integer findingsLow,
    Integer assetsScanned,
    String rawPayload,
    String errorMessage
) {}
