package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "exercises")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmExercise {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "exercise_name_en", nullable = false, length = 255)
    private String exerciseNameEn;

    @Column(name = "exercise_name_ar", length = 255)
    private String exerciseNameAr;

    @Column(name = "exercise_type", nullable = false, length = 50)
    private String exerciseType = "TABLETOP";

    @Column(name = "scenario", columnDefinition = "NVARCHAR(MAX)")
    private String scenario;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "executed_date")
    private LocalDate executedDate;

    @Column(name = "score")
    private Integer score;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "SCHEDULED";

    @Column(name = "findings", columnDefinition = "NVARCHAR(MAX)")
    private String findings;

    @Column(name = "capa_count", nullable = false)
    private Integer capaCount = 0;

    @Column(name = "facilitator", length = 255)
    private String facilitator;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
