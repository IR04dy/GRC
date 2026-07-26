package com.tss.grc.module.bcm.cycle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lesson learned — Sprint BCM-3. Sources: INCIDENT / CRISIS / EXERCISE / AUDIT / MANUAL. */
@Entity
@Table(schema = "bcm", name = "lessons_learned")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmLessonLearned {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "cycle_id")
    private Long cycleId;

    /** INCIDENT | CRISIS | EXERCISE | AUDIT | MANUAL */
    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "root_cause", columnDefinition = "NVARCHAR(MAX)")
    private String rootCause;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String recommendation;

    @Column(name = "related_plan_id")    private Long relatedPlanId;
    @Column(name = "related_risk_id")    private Long relatedRiskId;
    @Column(name = "related_control_id") private Long relatedControlId;
    @Column(name = "corrective_action_id") private Long correctiveActionId;
    @Column(name = "owner_id")           private Long ownerId;

    /** DRAFT | UNDER_REVIEW | APPROVED | REJECTED | CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 120)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvedBy;

    @Column(name = "approved_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime approvedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
