package com.tss.grc.module.bcm.mtpd_inheritance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 10-E — Header row for one MTPD-inheritance computation cycle.
 *
 * <p>Each row is an immutable snapshot of the bank's MTPD-inheritance
 * posture at compute time. Findings rows hang off this header and
 * carry the per-(process, vendor) breach/warning detail.
 *
 * <p>{@code isPublished} gates auditor / board-pack visibility: the
 * BCM Manager computes a draft, reviews it for false positives, then
 * publishes when satisfied. Drafts are operator-only.
 *
 * <p>Roll-ups ({@code totalProcesses}, {@code totalBreaches},
 * {@code totalWarnings}, {@code totalOk}, {@code totalNoAttestation})
 * are computed in {@code MtpdInheritanceService.computeAssessment()}
 * and frozen on the row — no live re-counting from findings.
 */
@Entity
@Table(schema = "bcm", name = "mtpd_inheritance_assessment")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MtpdInheritanceAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "assessment_code", nullable = false, length = 64)
    private String assessmentCode;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    @Column(name = "cycle_quarter", length = 10)
    private String cycleQuarter;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt = LocalDateTime.now();

    @Column(name = "computed_by_user_id")
    private Long computedByUserId;

    @Column(name = "total_processes", nullable = false)
    private Integer totalProcesses = 0;

    @Column(name = "total_breaches", nullable = false)
    private Integer totalBreaches = 0;

    @Column(name = "total_warnings", nullable = false)
    private Integer totalWarnings = 0;

    @Column(name = "total_ok", nullable = false)
    private Integer totalOk = 0;

    @Column(name = "total_no_attestation", nullable = false)
    private Integer totalNoAttestation = 0;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by_user_id")
    private Long publishedByUserId;

    @Column(name = "notes_en", length = 2000)
    private String notesEn;

    @Column(name = "notes_ar", length = 2000)
    private String notesAr;

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
