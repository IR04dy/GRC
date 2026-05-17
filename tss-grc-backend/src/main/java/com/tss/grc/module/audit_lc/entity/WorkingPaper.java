package com.tss.grc.module.audit_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Working paper — V16 table {@code audit_lc.working_papers}.
 *
 * <p>Per-engagement test work / interview notes / observations etc.
 * Reviewed via the peer-review workflow (IIA Std 2330):
 * DRAFT → PEER_REVIEW → APPROVED | REJECTED.
 */
@Entity
@Table(schema = "audit_lc", name = "working_papers")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WorkingPaper {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)     private String externalId;
    @Column(name = "organisation_id", nullable = false)              private Long organisationId;
    @Column(name = "engagement_code", nullable = false, length = 64) private String engagementCode;

    /** TEST | INTERVIEW | OBSERVATION | DOCUMENT_REVIEW | WALKTHROUGH */
    @Column(nullable = false, length = 20) private String type;

    @Column(name = "ref_number", nullable = false, length = 20) private String refNumber;
    @Column(name = "title_en", nullable = false, length = 500)  private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500)  private String titleAr;

    @Column(nullable = false) private String author;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    /** DRAFT | PEER_REVIEW | APPROVED | REJECTED */
    @Column(nullable = false, length = 20) private String status = "DRAFT";

    @Column private String reviewer;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;

    @Column(name = "procedure_en",  columnDefinition = "NVARCHAR(MAX)") private String procedureEn;
    @Column(name = "procedure_ar",  columnDefinition = "NVARCHAR(MAX)") private String procedureAr;
    @Column(name = "conclusion_en", columnDefinition = "NVARCHAR(MAX)") private String conclusionEn;
    @Column(name = "conclusion_ar", columnDefinition = "NVARCHAR(MAX)") private String conclusionAr;

    @Column(name = "controls_covered", columnDefinition = "NVARCHAR(MAX)") private String controlsCovered;

    @Column(precision = 6, scale = 2) private BigDecimal hours = BigDecimal.ZERO;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
