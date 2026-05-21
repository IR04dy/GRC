package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SIEM / SOAR inbound event — audit Tier-3 #21.
 *
 * <p>Captures the raw payload of an external webhook POST (Splunk, Sentinel,
 * QRadar, XSOAR, CrowdStrike, custom). The {@code SiemWebhookService}
 * evaluates each row and either escalates to a {@link CrisisEvent} or
 * marks it IGNORED based on severity + an internal rules table (future).
 *
 * <p>All rows are retained for audit even after escalation; the
 * {@code crisisEventId} soft FK ties an escalated row back to its event.
 */
@Entity
@Table(schema = "bcm", name = "siem_inbound_events")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SiemInboundEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** SPLUNK | SENTINEL | QRADAR | XSOAR | CROWDSTRIKE | CUSTOM */
    @Column(name = "source_system", nullable = false, length = 100)
    private String sourceSystem;

    @Column(name = "source_alert_id", length = 255)
    private String sourceAlertId;

    /** 1..5 — same scale as CrisisEvent.severity. */
    @Column(nullable = false)
    private Integer severity = 1;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String summary;

    @Column(name = "raw_payload_json", columnDefinition = "NVARCHAR(MAX)")
    private String rawPayloadJson;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /** RECEIVED | EVALUATED | ESCALATED | IGNORED | ERROR */
    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus = "RECEIVED";

    @Column(name = "crisis_event_id")
    private Long crisisEventId;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
