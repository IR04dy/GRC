package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * SIEM-ingested security event. One row per alert the SOC has to triage.
 *
 * <p>Most events arrive via the {@code POST /api/v1/socops/siem-events}
 * webhook in a vendor-neutral JSON shape. High-severity events (CRITICAL/HIGH)
 * auto-create an Incident downstream, with a soft FK back here via
 * {@link #incidentId} so the analyst can pivot between the SOC queue and
 * the incident record.
 *
 * <p>SLA timer ({@link #slaDueAt}) is set on insert from the NCA ECC-2
 * incident-response windows — Critical 1h, High 4h, Medium 24h — and the
 * F8 automation engine uses it to drive escalation reminders.
 */
@Entity
@Table(schema = "socops", name = "security_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SecurityEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "source_system", nullable = false, length = 60) private String sourceSystem;
    @Column(name = "event_time", nullable = false) private LocalDateTime eventTime;
    @Column(name = "ingested_at") private LocalDateTime ingestedAt;
    @Column(nullable = false, length = 20) private String severity;
    @Column(length = 60) private String category;
    @Column(name = "rule_name", length = 300) private String ruleName;
    @Column(name = "source_host", length = 255) private String sourceHost;
    @Column(name = "source_ip", length = 45) private String sourceIp;
    @Column(name = "destination_host", length = 255) private String destinationHost;
    @Column(name = "destination_ip", length = 45) private String destinationIp;
    @Column(length = 255) private String username;
    @Column(name = "asset_id") private Long assetId;
    @Column(name = "raw_payload", columnDefinition = "NVARCHAR(MAX)") private String rawPayload;
    @Column(nullable = false, length = 30) private String status = "NEW";
    @Column(name = "assigned_to_user_id") private Long assignedToUserId;
    @Column(name = "incident_id") private Long incidentId;
    @Column(name = "triaged_at") private LocalDateTime triagedAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @Column(name = "sla_due_at") private LocalDateTime slaDueAt;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
