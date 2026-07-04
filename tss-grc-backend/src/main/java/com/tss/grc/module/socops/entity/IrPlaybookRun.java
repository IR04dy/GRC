package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * A single execution of an {@link IrPlaybook} (epic SOC-B). Invoking a playbook
 * instantiates one run header plus one {@link IrPlaybookRunTask} per step, so the
 * SOC has an actual, clocked, owner-assigned to-do list to work through — not just
 * an invocation counter.
 *
 * <p>The run snapshots the playbook's code / title / MTTR target at invocation
 * time, so later edits to the template don't rewrite the operating history. It
 * advances as its tasks complete: {@code completedTasks} tracks progress and the
 * run flips {@code RUNNING → COMPLETED} once the last task is done.
 */
@Entity
@Table(schema = "socops", name = "ir_playbook_run")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IrPlaybookRun extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "playbook_id", nullable = false) private Long playbookId;
    @Column(name = "playbook_code", length = 40) private String playbookCode;
    @Column(name = "title_en", length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(nullable = false, length = 30) private String status = "RUNNING";
    @Column(name = "incident_id") private Long incidentId;
    @Column(name = "triggered_by_event_id") private Long triggeredByEventId;
    @Column(name = "invoked_by", length = 255) private String invokedBy;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "total_tasks", nullable = false) private Integer totalTasks = 0;
    @Column(name = "completed_tasks", nullable = false) private Integer completedTasks = 0;
    @Column(name = "target_mttr_minutes") private Integer targetMttrMinutes;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
