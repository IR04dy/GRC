package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * SitRep — append-only timeline entry for a {@link CrisisEvent}.
 *
 * <p>Crisis team logs these on a 30/60-minute cadence during a major event.
 * Each row is immutable once written; corrections are appended as new rows.
 * Optional {@code severityChange} field captures escalations / de-escalations
 * so the parent event's severity history is reconstructable from this table.
 */
@Entity
@Table(schema = "bcm", name = "crisis_situation_reports")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisSituationReport {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "crisis_event_id", nullable = false)
    private Long crisisEventId;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt = LocalDateTime.now();

    @Column(name = "reported_by")
    private Long reportedBy;

    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String summary;

    /** WORSENING | STABILISING | RECOVERING | NORMAL */
    @Column(name = "status_update", length = 50)
    private String statusUpdate;

    /** When this SitRep escalated or de-escalated the event severity (1..5). */
    @Column(name = "severity_change")
    private Integer severityChange;

    @Column(name = "attachments_json", columnDefinition = "NVARCHAR(MAX)")
    private String attachmentsJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
