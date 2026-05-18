package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Exercise-scenario catalog row — audit Tier-3 #16.
 *
 * <p>Pre-built exercise scenarios with timed injects + success criteria.
 * A facilitator picks one from the catalog and runs it; the actual exercise
 * execution (run-id, score, findings) lives on the existing legacy
 * {@code bcm.bcm_exercises} table — this entity is reference content only.
 */
@Entity
@Table(schema = "bcm", name = "exercise_scenarios")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmExerciseScenario {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_code", nullable = false, length = 50)
    private String scenarioCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;
    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "scenario_category", nullable = false, length = 50)
    private String scenarioCategory;

    /** TABLETOP | WALKTHROUGH | FUNCTIONAL | FAILOVER | SIMULATION | FULL_DR */
    @Column(name = "exercise_type", nullable = false, length = 30)
    private String exerciseType;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 90;

    @Column(name = "description_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "objectives_en", columnDefinition = "NVARCHAR(MAX)")
    private String objectivesEn;
    @Column(name = "objectives_ar", columnDefinition = "NVARCHAR(MAX)")
    private String objectivesAr;

    /** Ordered timed injects as JSON. See V111 for shape. */
    @Column(name = "injects_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String injectsJson;

    /** Ordered checklist scored at after-action review as JSON. */
    @Column(name = "success_criteria_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String successCriteriaJson;

    @Column(name = "role_assignments_json", columnDefinition = "NVARCHAR(MAX)")
    private String roleAssignmentsJson;

    @Column(name = "tags_json", columnDefinition = "NVARCHAR(MAX)")
    private String tagsJson;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
