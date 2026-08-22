package com.tss.grc.module.audit_eng.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

// Distinct JPA entity name avoids collision with the legacy
// com.tss.grc.module.compliance.entity.AuditEngagement.
@Entity(name = "AuditEngAuditEngagement")
@Table(schema = "audit_eng", name = "engagements")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AuditEngagement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "engagement_code", length = 32) private String engagementCode;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;
    @Column(name = "objective_en", columnDefinition = "NVARCHAR(MAX)") private String objectiveEn;
    @Column(name = "objective_ar", columnDefinition = "NVARCHAR(MAX)") private String objectiveAr;
    @Column(name = "scope_en", columnDefinition = "NVARCHAR(MAX)") private String scopeEn;
    @Column(name = "scope_ar", columnDefinition = "NVARCHAR(MAX)") private String scopeAr;

    /** INTERNAL/EXTERNAL/REGULATORY/CO_SOURCED */
    @Column(name = "audit_type", nullable = false, length = 30) private String auditType = "INTERNAL";
    @Column(length = 50) private String framework;
    @Column(name = "auditor_firm", length = 255) private String auditorFirm;
    @Column(name = "lead_auditor", nullable = false, length = 255) private String leadAuditor;
    @Column(name = "audit_team", columnDefinition = "NVARCHAR(MAX)") private String auditTeam;
    @Column(name = "audit_committee_chair", length = 255) private String auditCommitteeChair;

    /** PLANNING/FIELDWORK/REPORTING/FOLLOW_UP/CLOSED/CANCELLED */
    @Column(nullable = false, length = 30) private String status = "PLANNING";

    @Column(name = "planning_started_at", nullable = false) private LocalDateTime planningStartedAt = LocalDateTime.now();
    @Column(name = "fieldwork_started_at") private LocalDateTime fieldworkStartedAt;
    @Column(name = "fieldwork_ended_at") private LocalDateTime fieldworkEndedAt;
    @Column(name = "reporting_started_at") private LocalDateTime reportingStartedAt;
    @Column(name = "report_issued_at") private LocalDateTime reportIssuedAt;
    @Column(name = "follow_up_started_at") private LocalDateTime followUpStartedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason_en", columnDefinition = "NVARCHAR(MAX)") private String cancellationReasonEn;

    /** F2-275 (V389) - house rule 7. Nullable and NOT @NotBlank on purpose: bean validation fires on UPDATE too, so requiring Arabic would make every record predating V389 uneditable, and nothing can invent Arabic governance text (F2-30's reason). */
    @Column(name = "cancellation_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String cancellationReasonAr;

    @Column(name = "budget_hours") private Integer budgetHours;
    @Column(name = "actual_hours") private Integer actualHours;
    @Column(name = "findings_count", nullable = false) private Integer findingsCount = 0;

    @Column(name = "linked_finding_ids", columnDefinition = "NVARCHAR(MAX)") private String linkedFindingIds;
    @Column(name = "annual_plan_id", length = 64) private String annualPlanId;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
