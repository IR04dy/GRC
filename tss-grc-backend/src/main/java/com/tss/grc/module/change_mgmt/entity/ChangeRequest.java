package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "change_mgmt", name = "change_requests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "change_code", length = 50)
    private String changeCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType = "NORMAL"; // STANDARD | NORMAL | EMERGENCY

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "impact_summary", columnDefinition = "NVARCHAR(MAX)")
    private String impactSummary;

    @Column(name = "rollback_plan", columnDefinition = "NVARCHAR(MAX)")
    private String rollbackPlan;

    @Column(name = "test_plan", columnDefinition = "NVARCHAR(MAX)")
    private String testPlan;

    @Column(name = "business_justification", columnDefinition = "NVARCHAR(MAX)")
    private String businessJustification;

    @Column(name = "requested_by_id")
    private Long requestedById;

    @Column(name = "requested_by_name", length = 255)
    private String requestedByName;

    @Column(name = "change_owner_id")
    private Long changeOwnerId;

    @Column(name = "change_owner_name", length = 255)
    private String changeOwnerName;

    @Column(name = "implementer_name", length = 255)
    private String implementerName;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column(name = "implementation_started")
    private LocalDateTime implementationStarted;

    @Column(name = "implementation_ended")
    private LocalDateTime implementationEnded;

    @Column(name = "post_review_date")
    private LocalDate postReviewDate;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "DRAFT";

    @Column(name = "cab_decision_at")
    private LocalDateTime cabDecisionAt;

    @Column(name = "cab_decision_notes", columnDefinition = "NVARCHAR(MAX)")
    private String cabDecisionNotes;

    @Column(name = "outcome", length = 30)
    private String outcome;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
