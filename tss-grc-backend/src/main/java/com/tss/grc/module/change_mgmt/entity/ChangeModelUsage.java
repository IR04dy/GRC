package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * S8e / CE-2 — which change applied which model, and whether it deviated (V354).
 *
 * <p>A separate table rather than a column on {@code change_requests}, because the audit question is
 * not only <i>which model</i> but <i>did the change actually follow it</i>. A change that cites a
 * pre-approved model and then departs from it is <b>not</b> pre-approved, and
 * {@link #deviationNotes} is where that is recorded — the difference between a catalogue that
 * documents practice and one that launders it.
 *
 * <p>{@code ux_change_model_usage_one_per_change} makes "which model authorised this?" have exactly
 * one answer.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_model_usage")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeModelUsage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    @Column(name = "change_model_id", nullable = false)
    private Long changeModelId;

    @Column(name = "applied_at", insertable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "applied_by_id")
    private Long appliedById;

    @Column(name = "applied_by_name", length = 200)
    private String appliedByName;

    /** Null means the change followed the model. Non-blank means it did not, and says how. */
    @Column(name = "deviation_notes", columnDefinition = "NVARCHAR(MAX)")
    private String deviationNotes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** True when the change departed from the procedure it cited. */
    public boolean hasDeviation() {
        return deviationNotes != null && !deviationNotes.isBlank();
    }
}
