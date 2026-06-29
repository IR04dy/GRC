package com.tss.grc.module.evidence.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "evidence", name = "evidence_requests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EvidenceRequest extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "sub_control_id")
    private Long subControlId;

    /**
     * Generic entity anchor — lets a request target any aggregate (a BCM plan,
     * BIA, governance policy, crisis event, CAPA, …) instead of only a
     * compliance sub-control. Free-form, mirroring the generic attachments
     * table. Null for legacy compliance requests that use {@link #subControlId}.
     */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "requested_by_id")
    private Long requestedById;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    /** Owning department the evidence is requested from (alternative/additional to a named assignee). */
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(name = "status", nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "fulfilled_by_id")
    private Long fulfilledById;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    /** Last time the SLA scheduler sent an overdue reminder — used to throttle. */
    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;
}
