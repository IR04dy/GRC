package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * One actionable task within an {@link IrPlaybookRun} (epic SOC-B) — the runtime
 * instance of an {@link IrPlaybookStep}. Each task is owner-assigned
 * ({@code ownerRole}, from the step's responsible role) and clocked
 * ({@code dueAt}, cumulative from run start using the step's estimated minutes),
 * so the SOC can see who owns what and when it is due.
 *
 * <p>The task content is snapshotted from the step at invocation time. Completing
 * a task advances the parent run; a run whose tasks are all {@code DONE} becomes
 * {@code COMPLETED}.
 */
@Entity
@Table(schema = "socops", name = "ir_playbook_run_task")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IrPlaybookRunTask extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "playbook_id", nullable = false) private Long playbookId;
    @Column(name = "step_id") private Long stepId;
    @Column(name = "step_order", nullable = false) private Integer stepOrder;
    @Column(nullable = false, length = 40) private String phase;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "instructions_en", columnDefinition = "NVARCHAR(MAX)") private String instructionsEn;
    @Column(name = "instructions_ar", columnDefinition = "NVARCHAR(MAX)") private String instructionsAr;
    @Column(name = "owner_role", length = 120) private String ownerRole;
    @Column(nullable = false, length = 30) private String status = "PENDING";
    @Column(name = "is_critical", nullable = false) private Boolean isCritical = false;
    @Column(name = "estimated_minutes") private Integer estimatedMinutes;
    @Column(name = "due_at") private LocalDateTime dueAt;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "completed_by", length = 255) private String completedBy;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
