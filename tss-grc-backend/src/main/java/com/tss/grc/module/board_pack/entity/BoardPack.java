package com.tss.grc.module.board_pack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Distinct JPA entity name in case the legacy reporting module ever adds one.
@Entity(name = "BoardPackBoardPack")
@Table(schema = "board_pack", name = "packs")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BoardPack {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;

    @Column(name = "period_label", nullable = false, length = 40) private String periodLabel;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;

    /** BOARD/AUDIT_COMMITTEE/RISK_COMMITTEE/EXEC */
    @Column(nullable = false, length = 40) private String audience = "BOARD";

    @Column(name = "risk_summary_json",      columnDefinition = "NVARCHAR(MAX)") private String riskSummaryJson;
    @Column(name = "appetite_breaches_json", columnDefinition = "NVARCHAR(MAX)") private String appetiteBreachesJson;
    @Column(name = "audit_engagements_json", columnDefinition = "NVARCHAR(MAX)") private String auditEngagementsJson;
    @Column(name = "incidents_json",         columnDefinition = "NVARCHAR(MAX)") private String incidentsJson;
    @Column(name = "pdpl_summary_json",      columnDefinition = "NVARCHAR(MAX)") private String pdplSummaryJson;
    @Column(name = "ccm_summary_json",       columnDefinition = "NVARCHAR(MAX)") private String ccmSummaryJson;
    @Column(name = "sla_summary_json",       columnDefinition = "NVARCHAR(MAX)") private String slaSummaryJson;
    @Column(name = "custom_sections_json",   columnDefinition = "NVARCHAR(MAX)") private String customSectionsJson;

    /** DRAFT/IN_REVIEW/APPROVED/PUBLISHED/CANCELLED */
    @Column(nullable = false, length = 30) private String status = "DRAFT";

    @Column(name = "drafted_by", nullable = false, length = 255) private String draftedBy;
    @Column(name = "drafted_at", nullable = false) private LocalDateTime draftedAt = LocalDateTime.now();
    @Column(name = "submitted_for_review_at") private LocalDateTime submittedForReviewAt;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "reviewed_by", length = 255) private String reviewedBy;
    @Column(name = "review_comments_en", columnDefinition = "NVARCHAR(MAX)") private String reviewCommentsEn;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "approved_by", length = 255) private String approvedBy;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "publication_url", length = 500) private String publicationUrl;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason_en", columnDefinition = "NVARCHAR(MAX)") private String cancellationReasonEn;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
