package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(schema = "admin", name = "committees")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Committee {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "committee_type", nullable = false, length = 30)
    private String committeeType;

    @Column(name = "chairperson_name", length = 255)
    private String chairpersonName;

    @Column(name = "chairperson_role", length = 100)
    private String chairpersonRole;

    @Column(name = "secretary_name", length = 255)
    private String secretaryName;

    @Column(name = "members_json", columnDefinition = "NVARCHAR(MAX)")
    private String membersJson;

    @Column(name = "charter_en", columnDefinition = "NVARCHAR(MAX)")
    private String charterEn;

    @Column(name = "charter_ar", columnDefinition = "NVARCHAR(MAX)")
    private String charterAr;

    @Column(name = "meeting_cadence", nullable = false, length = 20)
    private String meetingCadence = "MONTHLY";

    @Column(name = "quorum_pct")
    private Integer quorumPct;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "dissolved_at")
    private LocalDateTime dissolvedAt;

    @Column(name = "dissolved_reason", length = 500)
    private String dissolvedReason;

    @Column(name = "external_id", length = 64)
    private String externalId;

    // ── V154 extensions — template instantiation + reporting line ────
    /** Which V154 template did this committee instantiate from (if any). */
    @Column(name = "charter_template_code", length = 80)
    private String charterTemplateCode;

    /** Sub-committee delegation — points at parent committee.id. */
    @Column(name = "parent_committee_id")
    private Long parentCommitteeId;

    /** Reporting line — e.g. Risk Committee reports to Board. */
    @Column(name = "reports_to_committee_id")
    private Long reportsToCommitteeId;

    /** BANKING | INSURANCE | CMA | PUBLIC | HEALTHCARE | GENERAL */
    @Column(name = "regulatory_domain", length = 40)
    private String regulatoryDomain;

    /** Comma-separated: SAMA, CMA, NCA, PDPL, NDMO, SDAIA, ZATCA, ISO22301, … */
    @Column(name = "regulator_anchors", length = 500)
    private String regulatorAnchors;

    /** Annual charter review due-date — drives an F8 reminder 30 days ahead. */
    @Column(name = "next_charter_review_at")
    private java.time.LocalDate nextCharterReviewAt;

    /** Board resolution that established this committee. */
    @Column(name = "formation_resolution_code", length = 80)
    private String formationResolutionCode;

    /** When the year-ahead calendar was finalised — drives "calendar already
     *  generated" detection in the UI. */
    @Column(name = "annual_calendar_locked_at")
    private LocalDateTime annualCalendarLockedAt;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at")                   private LocalDateTime deletedAt;
    @Column(name = "deleted_by")                   private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
