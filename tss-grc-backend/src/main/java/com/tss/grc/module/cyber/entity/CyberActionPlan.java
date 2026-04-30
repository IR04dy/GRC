package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_action_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberActionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "title_ar")
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_ref")
    private String sourceRef;

    private String priority;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "workflow_status", nullable = false)
    private String workflowStatus = "OPEN";

    @Column(name = "progress_pct")
    private int progressPct = 0;

    @Column(name = "completion_evidence", columnDefinition = "NVARCHAR(MAX)")
    private String completionEvidence;

    @Column(name = "validation_notes", columnDefinition = "NVARCHAR(MAX)")
    private String validationNotes;

    @Column(name = "linked_control_id")
    private Long linkedControlId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
