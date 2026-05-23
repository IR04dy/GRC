package com.tss.grc.module.integrations.splunk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Audit row for a single HEC POST attempt. Backs the V165
 * {@code platform.splunk_hec_outbound_log} table.
 *
 * <p>One row per attempt — a retry burst of 3 produces 3 rows. The
 * {@link #attemptCount} field on each row makes "final attempt" obvious
 * (and the row's success/failure dictates whether further retries follow).
 *
 * <p>Soft-deleted via {@code is_deleted}. Like {@code IntegrationExternalLink}
 * this entity doesn't extend {@code BaseEntity} — the audit row is a
 * thin append-only record and doesn't need updated_at / version columns.
 */
@Entity
@Table(name = "splunk_hec_outbound_log", schema = "platform")
@SQLRestriction("is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SplunkHecOutboundLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "source_module", nullable = false, length = 40)
    private String sourceModule;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Column(name = "hec_index", length = 120)
    private String hecIndex;

    @Column(name = "event_payload", columnDefinition = "NVARCHAR(MAX)")
    private String eventPayload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
