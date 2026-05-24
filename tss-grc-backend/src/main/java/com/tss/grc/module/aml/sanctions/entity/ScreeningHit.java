package com.tss.grc.module.aml.sanctions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A single match between a screened subject and a list entry.
 *
 * <p>Lifecycle ({@code status} column):
 * {@code PENDING_REVIEW → TRUE_POSITIVE | FALSE_POSITIVE | ESCALATED →
 * BLOCK_RECOMMENDED | RELEASE_RECOMMENDED → CLOSED}. The full state machine
 * is enforced by {@code SanctionsHitService.reviewHit}.
 *
 * <p>SLA: a hit in {@code PENDING_REVIEW} more than 24 hours after
 * {@code created_at} is marked {@code sla_breach=1} by the scheduler.
 */
@Entity
@Table(schema = "aml", name = "screening_hit")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ScreeningHit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "check_id", nullable = false)
    private Long checkId;

    @Column(name = "list_entry_id", nullable = false)
    private Long listEntryId;

    @Column(name = "match_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal matchScore;

    /** EXACT_NAME | FUZZY_NAME | ALIAS | DOB_MATCH | DOCUMENT_MATCH |
     *  COUNTRY_MATCH | COMPOSITE */
    @Column(name = "match_type", nullable = false, length = 40)
    private String matchType;

    /** PENDING_REVIEW | TRUE_POSITIVE | FALSE_POSITIVE | ESCALATED |
     *  BLOCK_RECOMMENDED | RELEASE_RECOMMENDED | CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_REVIEW";

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "disposition_en", length = 2000)
    private String dispositionEn;

    @Column(name = "disposition_ar", length = 2000)
    private String dispositionAr;

    @Column(name = "sla_breach", nullable = false)
    private Boolean slaBreach = false;

    @Column(name = "escalated_to_user_id")
    private Long escalatedToUserId;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "capa_task_id")
    private Long capaTaskId;

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
