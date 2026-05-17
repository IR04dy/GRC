package com.tss.grc.module.sust.cma.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * CMA Articles 91–94 disclosure item (V60 — slice 6 of the
 * Sustainability module). Promotes the canonical 11 disclosures from a
 * static frontend constant to a backend-driven entity with gap tracking,
 * evidence linkage, and an auditable lifecycle.
 *
 * <p>Disclosure statuses:
 * <pre>
 *   DISCLOSED          full disclosure with evidence
 *   PARTIAL            disclosure exists but incomplete (Rule 5: gap required)
 *   NOT_DISCLOSED      missing entirely (Rule 5: gap required)
 *   NOT_APPLICABLE     formally excluded
 *   UNDER_REVIEW       reviewer assessing the disclosure quality
 *   EVIDENCE_REQUIRED  disclosed but evidence_ref empty (Rule 6 auto-tag)
 * </pre>
 *
 * <p>Service rules:
 * <ul>
 *   <li><b>Rule 5</b> — transitioning to PARTIAL or NOT_DISCLOSED
 *       requires a gap description (en or ar).</li>
 *   <li><b>Rule 6</b> — DISCLOSED/PARTIAL with empty evidenceRef is
 *       auto-rewritten to EVIDENCE_REQUIRED on save.</li>
 * </ul>
 */
@Entity
@Table(schema = "sust", name = "cma_disclosure")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CmaDisclosure {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;
    @Column(nullable = false, length = 10)             private String article;
    @Column(length = 10)                                private String clause;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(name = "requirement_en", columnDefinition = "NVARCHAR(MAX)") private String requirementEn;
    @Column(name = "requirement_ar", columnDefinition = "NVARCHAR(MAX)") private String requirementAr;

    @Column(name = "disclosure_status", nullable = false, length = 30)
    private String disclosureStatus = "UNDER_REVIEW";

    @Column(name = "evidence_ref",        length = 255) private String evidenceRef;
    @Column(name = "evidence_owner",      length = 255) private String evidenceOwner;
    @Column(name = "responsible_department", length = 120) private String responsibleDepartment;

    @Column(name = "gap_description_en", columnDefinition = "NVARCHAR(MAX)") private String gapDescriptionEn;
    @Column(name = "gap_description_ar", columnDefinition = "NVARCHAR(MAX)") private String gapDescriptionAr;

    @Column(name = "linked_capa_id") private Long linkedCapaId;

    @Column(name = "report_section_reference", length = 120) private String reportSectionReference;

    @Column(name = "reviewer_name", length = 255)  private String reviewerName;
    @Column(name = "reviewer_email", length = 255) private String reviewerEmail;
    @Column(name = "last_reviewed_at")              private LocalDateTime lastReviewedAt;
    @Column(name = "approval_status", length = 30)  private String approvalStatus;
    @Column(name = "approved_by", length = 255)     private String approvedBy;
    @Column(name = "approved_at")                    private LocalDateTime approvedAt;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    /** Composite display key, e.g. "91(a)" or "94". Used by the UI label. */
    @Transient
    public String getDisplayCode() {
        return article + (clause == null ? "" : clause);
    }
}
