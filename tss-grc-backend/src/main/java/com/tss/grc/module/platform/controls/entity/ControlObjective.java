package com.tss.grc.module.platform.controls.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Control Objective — a group of controls that collectively satisfy
 * a higher-level goal (e.g. "Privileged Access Management" objective
 * groups five controls).
 *
 * <p>Lifecycle: DRAFT → ACTIVE → RETIRED.
 *
 * <p>See {@code docs/grc/framework-intersection-plan.md} §5.
 */
@Entity
@Table(schema = "platform", name = "control_objectives")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ControlObjective {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "objective_code", nullable = false, length = 60)
    private String objectiveCode;

    @Column(nullable = false, length = 40)
    private String domain;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
