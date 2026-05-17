package com.tss.grc.module.ndi.gap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Corrective Action (CAPA) — slice 8. One row per remediation task.
 * Multiple CAPAs per gap allowed; the service rolls completed/total CAPAs
 * back to the parent gap on every write.
 */
@Entity
@Table(schema = "ndi", name = "corrective_action")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiCorrectiveAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "gap_id", nullable = false) private Long gapId;
    @Column(name = "action_code", length = 40) private String actionCode;

    @Column(name = "action_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String actionEn;
    @Column(name = "action_ar",                   columnDefinition = "NVARCHAR(MAX)") private String actionAr;
    @Column(name = "action_type", nullable = false, length = 30)                     private String actionType;

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;

    @Column(name = "target_date")     private LocalDate targetDate;
    @Column(name = "completion_date") private LocalDate completionDate;

    @Column(name = "progress_pct", nullable = false) private Integer progressPct;
    @Column(nullable = false, length = 20)           private String status;

    @Column(name = "evidence_id")                                       private Long evidenceId;
    @Column(name = "blocker_note", columnDefinition = "NVARCHAR(MAX)")  private String blockerNote;
    @Column(columnDefinition = "NVARCHAR(MAX)")                         private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)   createdAt = LocalDateTime.now();
        if (actionType == null)  actionType = "CONTROL";
        if (status == null)      status = "NOT_STARTED";
        if (progressPct == null) progressPct = 0;
        if (isDeleted == null)   isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
