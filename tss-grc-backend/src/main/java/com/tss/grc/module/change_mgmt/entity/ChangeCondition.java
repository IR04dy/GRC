package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * S7e / CE-5 — a condition the approving authority attached to a change (V352).
 *
 * <p>"Approved subject to the DR rehearsal being re-run" used to live in
 * {@code cab_decision_notes}: free text nothing reads, nobody owns and no gate consults. This is
 * that sentence as a record, with an imposer, an owner, a due date and a closing act — and
 * {@code SCHEDULED → IMPLEMENTING} is refused while one is {@code OPEN}.
 *
 * <p><b>Not a {@link ChangeTask}.</b> A task is a runbook step performed *during* implementation
 * (41 rows in each database: "Cut DNS to KSA region", "Promote KSA replica"), and 24 of 41 in dev
 * are legitimately {@code PENDING} when implementation starts. A guard over tasks would refuse every
 * change the platform holds. A condition is the inverse: an obligation imposed by the committee that
 * must be discharged *before* implementation.
 *
 * <p><b>Parented by the change, not by a decision row</b> — the uplift plan's
 * {@code change_decision_condition} hangs off a {@code ChangeDecision} that does not exist, because
 * S7c shipped the write-back as columns on {@code change_requests}.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_condition")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeCondition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Carried explicitly, unlike {@code change_tasks} — the register is read tenant-wide. */
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @NotBlank
    @Column(name = "text_en", nullable = false, length = 1000)
    private String textEn;

    /** House rule 7 — mandatory, and NOT NULL in the schema as well (V352 self-check 2). */
    @NotBlank
    @Column(name = "text_ar", nullable = false, length = 1000)
    private String textAr;

    @Column(name = "imposed_by_id")
    private Long imposedById;

    @Column(name = "imposed_by_name", length = 255)
    private String imposedByName;

    @Column(name = "imposed_at", nullable = false)
    private LocalDateTime imposedAt;

    @Column(name = "owner_name", length = 255)
    private String ownerName;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /** {@link com.tss.grc.module.change_mgmt.service.ChangeConditionStatus} — OPEN|SATISFIED|WAIVED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    // One author trio for the closing act: SATISFIED and WAIVED are mutually exclusive, `status`
    // says which, and `ck_cc_closure` refuses a closed row with no author (F2-103 in the schema).
    @Column(name = "closed_by_id")
    private Long closedById;

    @Column(name = "closed_by_name", length = 255)
    private String closedByName;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_notes", columnDefinition = "NVARCHAR(MAX)")
    private String closureNotes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (imposedAt == null) imposedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
