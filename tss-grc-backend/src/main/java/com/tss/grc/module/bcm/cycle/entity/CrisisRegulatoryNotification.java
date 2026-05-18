package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Regulator notification log — captures every regulator notification raised
 * during a {@link CrisisEvent} with reference number + acknowledgement tracking.
 *
 * <p>Critical for SAMA / NCEMA / NCA / CMA / PDPL audit trails. Each row records
 * who notified whom, when, how, and whether the regulator acknowledged receipt
 * (with their issued reference). Attachments JSON points at the evidence records
 * carrying any submitted artefacts.
 */
@Entity
@Table(schema = "bcm", name = "crisis_regulatory_notifications")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisRegulatoryNotification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "crisis_event_id", nullable = false)
    private Long crisisEventId;

    /** NCEMA | SAMA | NCA | CMA | PDPL | CIVIL_DEFENCE | MOH | MOE | OTHER */
    @Column(nullable = false, length = 50)
    private String regulator;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt = LocalDateTime.now();

    @Column(name = "notified_by")
    private Long notifiedBy;

    /** EMAIL | PORTAL | PHONE | LETTER | API */
    @Column(name = "notification_method", length = 50)
    private String notificationMethod;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "body_summary", columnDefinition = "NVARCHAR(MAX)")
    private String bodySummary;

    @Column(name = "acknowledgement_received", nullable = false)
    private Boolean acknowledgementReceived = false;

    @Column(name = "acknowledgement_at")
    private LocalDateTime acknowledgementAt;

    @Column(name = "acknowledgement_reference", length = 100)
    private String acknowledgementReference;

    @Column(name = "attachments_json", columnDefinition = "NVARCHAR(MAX)")
    private String attachmentsJson;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
