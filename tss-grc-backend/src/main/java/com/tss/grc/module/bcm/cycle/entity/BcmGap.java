package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** BCM gap — Sprint BCM-3. */
@Entity
@Table(schema = "bcm", name = "gaps")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmGap {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "cycle_id")
    private Long cycleId;

    /** BIA | BCP | DRP | EXERCISE | CALL_TREE | AUDIT | RISK | MANUAL */
    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "related_record_type", length = 120)
    private String relatedRecordType;

    @Column(name = "related_record_id")
    private Long relatedRecordId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "root_cause", columnDefinition = "NVARCHAR(MAX)")
    private String rootCause;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String impact;

    @Column(name = "related_risk_id")
    private Long relatedRiskId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** OPEN | ASSIGNED | IN_PROGRESS | UNDER_REVIEW | CLOSED | ACCEPTED_RISK | REOPENED */
    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
