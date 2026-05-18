package com.tss.grc.module.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * External audit lifecycle entity — assessment §5.3 recommendation #3.
 *
 * Status flow:
 *   PLANNED → ENGAGED → OPENING_MEETING → FIELDWORK → REPORTING
 *           → MGMT_RESPONSE → CLOSED → FOLLOW_UP
 */
@Entity(name = "ComplianceAuditEngagement")
@Table(schema = "compliance", name = "audit_engagements")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AuditEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "engagement_code", length = 50)
    private String engagementCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "audit_type", nullable = false, length = 30)
    private String auditType; // INTERNAL | EXTERNAL | REGULATORY | CERTIFICATION

    @Column(name = "framework", length = 100)
    private String framework;

    @Column(name = "auditor_firm", length = 255)
    private String auditorFirm;

    @Column(name = "lead_auditor", length = 255)
    private String leadAuditor;

    @Column(name = "engagement_letter_date")
    private LocalDate engagementLetterDate;

    @Column(name = "opening_meeting_date")
    private LocalDate openingMeetingDate;

    @Column(name = "fieldwork_start_date")
    private LocalDate fieldworkStartDate;

    @Column(name = "fieldwork_end_date")
    private LocalDate fieldworkEndDate;

    @Column(name = "report_issued_date")
    private LocalDate reportIssuedDate;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "next_followup_date")
    private LocalDate nextFollowupDate;

    @Column(name = "scope_summary", columnDefinition = "NVARCHAR(MAX)")
    private String scopeSummary;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "PLANNED";

    @Column(name = "findings_count", nullable = false)
    private Integer findingsCount = 0;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
