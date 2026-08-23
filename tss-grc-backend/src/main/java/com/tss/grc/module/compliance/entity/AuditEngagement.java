package com.tss.grc.module.compliance.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // F2-286 — the column is insertable = false, so the DATABASE refuses a caller's value
    // and the create hands back the instance Jackson bound: without this the response
    // echoed a value the row does not carry, and only a re-fetch showed the truth. This
    // says the mapping refuses it, not that the caller should never state it — where the
    // caller should, the fix is to make the column insertable (F2-286 did that for
    // ethics.screening_hits.screened_at, whose value nothing could ever state).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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
    // ⚠⚠ F2-294 removed `= "PLANNED"`. THE SHARPEST SITE IN THIS SWEEP: `update` guards
    // this and the initialiser made the guard fire on every entity-bound PUT, so a partial
    // body walked an ISO 27001 external audit engagement back to PLANNED — 10 of 12 live
    // rows in both databases, two of them CLOSED. `POST /audit-engagements/{id}/advance`
    // is the declared route for that move and carries the same COMPLIANCE:WRITE
    // permission, so the object-level machine was the only control and this PUT sat beside
    // it without one (F2-219's shape); `compliance.audit_engagements` has ZERO CHECK
    // constraints, so nothing else narrowed it either. `create` owns the default now.
    private String status;

    @Column(name = "findings_count", nullable = false)
    // ⚠ F2-294 removed `= 0`. `AuditEngagementRequest` — the only client type that binds
    // this route — does not declare `findingsCount` at all, so the form could never send
    // it and the guard fired with 0 on every request. 0 of 12 rows differ, so it was
    // latent; F2-278 filed the same field as FROZEN from the other direction, and the
    // sibling register derives it from its linked finding ids rather than storing it.
    private Integer findingsCount;

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
