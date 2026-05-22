package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Vulnerability scanner ingest run. Connectors (Tenable / Qualys / Defender /
 * Rapid7 / Wiz / Prisma Cloud) POST scan completions to
 * {@code /api/v1/socops/scanner-runs}; this row is the receipt + summary.
 * The detailed findings live in the existing cyber.vulnerability tables.
 */
@Entity
@Table(schema = "socops", name = "scanner_run")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ScannerRun extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(nullable = false, length = 40) private String scanner;
    @Column(name = "scan_name", length = 300) private String scanName;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String scope;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(nullable = false, length = 30) private String status = "RUNNING";
    @Column(name = "findings_total", nullable = false) private Integer findingsTotal = 0;
    @Column(name = "findings_critical", nullable = false) private Integer findingsCritical = 0;
    @Column(name = "findings_high", nullable = false) private Integer findingsHigh = 0;
    @Column(name = "findings_medium", nullable = false) private Integer findingsMedium = 0;
    @Column(name = "findings_low", nullable = false) private Integer findingsLow = 0;
    @Column(name = "assets_scanned", nullable = false) private Integer assetsScanned = 0;
    @Column(name = "ingested_at") private LocalDateTime ingestedAt;
    @Column(name = "raw_payload", columnDefinition = "NVARCHAR(MAX)") private String rawPayload;
    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)") private String errorMessage;
}
