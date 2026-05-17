package com.tss.grc.module.privacy.bridge.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Audit-trail row capturing one execution of the cyber → PDPL bridge rule
 * (gap-analysis item A). Every classification of a {@code CyberIncident}
 * as PII-affecting writes one of these. Read-only from the UI.
 */
@Entity
@Table(schema = "privacy", name = "cyber_breach_bridge_log")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberBreachBridgeLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** BRIDGE_FIRED | BRIDGE_SKIPPED | BREACH_CREATED | BREACH_LINKED | ERROR. */
    @Column(name = "event_code", nullable = false, length = 64)
    private String eventCode;

    @Column(name = "cyber_incident_id")
    private Long cyberIncidentId;

    @Column(name = "cyber_incident_number", length = 64)
    private String cyberIncidentNumber;

    @Column(name = "data_breach_id")
    private Long dataBreachId;

    @Column(name = "data_breach_external_id", length = 64)
    private String dataBreachExternalId;

    @Column(name = "rule_name", nullable = false, length = 64)
    private String ruleName = "CYBER_TO_PDPL_V1";

    @Column(name = "trigger_reason", length = 500)
    private String triggerReason;

    /** CREATED | LINKED | SKIPPED | ERROR. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "outcome_detail", columnDefinition = "NVARCHAR(MAX)")
    private String outcomeDetail;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt = LocalDateTime.now();

    @Column(name = "triggered_by")
    private Long triggeredBy;

    @Column(name = "triggered_by_username", length = 255)
    private String triggeredByUsername;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
