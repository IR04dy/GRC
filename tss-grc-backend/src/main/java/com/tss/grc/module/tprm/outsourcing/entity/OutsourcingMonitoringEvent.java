package com.tss.grc.module.tprm.outsourcing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A monitoring event flagged during BAU operation of an
 * outsourcing arrangement (SLA breach, audit report, financial-health
 * flag, regulatory query, etc.).
 *
 * <p>SLA_BREACH events specifically trigger the
 * {@code OUTSOURCING_SLA_BREACH} F8 event so they can fan out to the
 * Audit Committee dashboard and the action-item queue.
 */
@Entity
@Table(schema = "tprm", name = "outsourcing_monitoring_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OutsourcingMonitoringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "arrangement_id", nullable = false)
    private Long arrangementId;

    /**
     * KPI_REVIEW | SLA_BREACH | INCIDENT | AUDIT_REPORT |
     * CONTRACT_AMENDMENT | FINANCIAL_HEALTH_FLAG | REGULATORY_QUERY |
     * CHANGE_OF_CONTROL | RFP_RECOMPETE | EXIT_TRIGGER.
     */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** INFO | MINOR | MODERATE | MAJOR | CRITICAL. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "INFO";

    @Column(name = "description_en", nullable = false, length = 2000)
    private String descriptionEn;

    @Column(name = "description_ar", nullable = false, length = 2000)
    private String descriptionAr;

    @Column(name = "action_taken_en", length = 1000)
    private String actionTakenEn;

    @Column(name = "action_taken_ar", length = 1000)
    private String actionTakenAr;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
