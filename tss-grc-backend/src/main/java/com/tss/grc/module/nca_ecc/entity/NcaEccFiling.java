package com.tss.grc.module.nca_ecc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "nca_ecc", name = "filings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NcaEccFiling {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "fiscal_year", nullable = false) private Integer fiscalYear;
    @Column(name = "framework_version", nullable = false, length = 20) private String frameworkVersion = "ECC-2:2024";

    @Column(name = "title_en", length = 500) private String titleEn;
    @Column(name = "title_ar", length = 500) private String titleAr;

    @Column(name = "domain1_score") private Integer domain1Score;
    @Column(name = "domain2_score") private Integer domain2Score;
    @Column(name = "domain3_score") private Integer domain3Score;
    @Column(name = "domain4_score") private Integer domain4Score;
    @Column(name = "domain5_score") private Integer domain5Score;
    @Column(name = "overall_score", precision = 4, scale = 2) private BigDecimal overallScore;

    @Column(name = "responses_json",   columnDefinition = "NVARCHAR(MAX)") private String responsesJson;
    @Column(name = "gap_summary_en",   columnDefinition = "NVARCHAR(MAX)") private String gapSummaryEn;
    @Column(name = "gap_summary_ar",   columnDefinition = "NVARCHAR(MAX)") private String gapSummaryAr;
    @Column(name = "capa_count", nullable = false) private Integer capaCount = 0;

    /** DRAFT/PEER_REVIEW/CISO_APPROVAL/SUBMITTED/ACKNOWLEDGED/CANCELLED */
    @Column(nullable = false, length = 30) private String status = "DRAFT";

    @Column(name = "drafted_by", nullable = false, length = 255) private String draftedBy;
    @Column(name = "drafted_at", nullable = false) private LocalDateTime draftedAt = LocalDateTime.now();
    @Column(name = "peer_review_started_at") private LocalDateTime peerReviewStartedAt;
    @Column(name = "peer_reviewer", length = 255) private String peerReviewer;
    @Column(name = "peer_review_completed_at") private LocalDateTime peerReviewCompletedAt;
    @Column(name = "peer_review_comments_en", columnDefinition = "NVARCHAR(MAX)") private String peerReviewCommentsEn;
    @Column(name = "ciso_approval_at") private LocalDateTime cisoApprovalAt;
    @Column(name = "ciso_approver", length = 255) private String cisoApprover;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(name = "submission_reference", length = 100) private String submissionReference;
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason_en", columnDefinition = "NVARCHAR(MAX)") private String cancellationReasonEn;

    /** F2-275 (V389) - house rule 7. Nullable and NOT @NotBlank on purpose: bean validation fires on UPDATE too, so requiring Arabic would make every record predating V389 uneditable, and nothing can invent Arabic governance text (F2-30's reason). */
    @Column(name = "cancellation_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String cancellationReasonAr;

    @Column(name = "linked_capa_ids", columnDefinition = "NVARCHAR(MAX)") private String linkedCapaIds;
    @Column(name = "linked_evidence", columnDefinition = "NVARCHAR(MAX)") private String linkedEvidence;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
