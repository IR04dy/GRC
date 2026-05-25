package com.tss.grc.module.tprm.outsourcing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — The exit plan attached to an outsourcing arrangement.
 *
 * <p>SAMA Outsourcing §6.1 requires a documented exit plan for every
 * material arrangement, tested at the cadence the bank determines is
 * commensurate with the criticality of the function. The
 * {@code exit_steps_json} column stores an ordered list of steps as
 * JSON ({@code [{ "step": "...", "owner": "...",
 * "target_date_relative_days": 30 }, ...]}); the service layer can
 * materialise these into action items when an exit is triggered.
 */
@Entity
@Table(schema = "tprm", name = "outsourcing_exit_plan")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OutsourcingExitPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "arrangement_id", nullable = false)
    private Long arrangementId;

    @Column(name = "trigger_conditions_en", nullable = false, length = 2000)
    private String triggerConditionsEn;

    @Column(name = "trigger_conditions_ar", nullable = false, length = 2000)
    private String triggerConditionsAr;

    @Column(name = "exit_steps_json", columnDefinition = "NVARCHAR(MAX)")
    private String exitStepsJson;

    @Column(name = "data_repatriation_plan_en", length = 2000)
    private String dataRepatriationPlanEn;

    @Column(name = "data_repatriation_plan_ar", length = 2000)
    private String dataRepatriationPlanAr;

    /** NEVER | ANNUAL | BIANNUAL | NEVER_TESTED. */
    @Column(name = "test_frequency", nullable = false, length = 20)
    private String testFrequency = "NEVER_TESTED";

    @Column(name = "last_tested_date")
    private LocalDate lastTestedDate;

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
