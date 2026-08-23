package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "bcm_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_name_en", nullable = false, length = 255)
    private String planNameEn;

    @Column(name = "plan_name_ar", length = 255)
    private String planNameAr;

    @Column(name = "plan_type", length = 100)
    private String planType;

    @Column(name = "version", length = 50)
    private String version;

    /**
     * DRAFT | UNDER_REVIEW | APPROVED | INVOKED | ARCHIVED (see {@code BcmService}).
     *
     * <p>F2-298 — deliberately NOT initialised, and this is the FIFTH instance of the same
     * residue. {@code PUT /api/v1/bcm/legacy-plans/{id}} binds this entity, and F2-234/F2-296
     * made {@code BcmService#updatePlan} tolerate an ECHO of the stored status and refuse a
     * CHANGE. With {@code = "DRAFT"} here, a body that OMITTED status deserialised to "DRAFT",
     * so the refusal fired on every plan that was not already DRAFT and answered 400 naming a
     * transition the caller never asked for — the same misleading diagnostic F2-297 found on
     * {@code BcpPlan}, one register over.
     *
     * <p>F2-238 removed this initialiser from {@code CrisisEvent}, {@code Issue} and
     * {@code BcmBiaAssessment}; F2-297 found the fourth on {@code BcpPlan}; this is the fifth.
     * {@code BcmService#createPlan} already owns the default through
     * {@code ServerOwnedState.requireInitial} with {@code INITIAL_PLAN_STATUS}, so the
     * initialiser was pure residue on create and a refusal on update. The column is NOT NULL and
     * the create writes it — do not restore it.
     */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_by")
    private Long deletedBy;
}
