package com.tss.grc.module.sharia.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Chronological timeline event for the Sharia module.
 *
 * <p>Each row links to a single subject (scholar / resolution / audit
 * finding / non-compliance event / product approval).
 */
@Entity
@Table(schema = "sharia", name = "sharia_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ShariaEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "scholar_id")
    private Long scholarId;

    @Column(name = "resolution_id")
    private Long resolutionId;

    @Column(name = "audit_finding_id")
    private Long auditFindingId;

    @Column(name = "nc_event_id")
    private Long ncEventId;

    @Column(name = "product_approval_id")
    private Long productApprovalId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 256)
    private String actorName;

    @Column(name = "status_after", length = 32)
    private String statusAfter;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;
}
