package com.tss.grc.module.regchange.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Distinct JPA entity name + Spring bean name so we don't collide with
// pre-existing com.tss.grc.module.compliance.entity.RegulatoryChange.
@Entity(name = "RegchangeRegulatoryChange")
@Table(schema = "regchange", name = "changes")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegulatoryChange {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    /** NCA/SAMA/CMA/SDAIA/CITC/ZATCA/MoH/HRSD/MoC/NAZAHA/OTHER */
    @Column(nullable = false, length = 20) private String regulator;
    @Column(length = 255) private String citation;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;
    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)") private String summaryEn;
    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)") private String summaryAr;
    @Column(name = "source_url", length = 500) private String sourceUrl;
    @Column(name = "published_at") private LocalDate publishedAt;
    @Column(name = "effective_at") private LocalDate effectiveAt;

    /** NEW/ASSESSED/IMPACT_ANALYSED/IN_IMPLEMENTATION/IMPLEMENTED/CLOSED/DEPRIORITISED */
    @Column(nullable = false, length = 30) private String status = "NEW";

    @Column(name = "assessed_at") private LocalDateTime assessedAt;
    @Column(name = "assessed_by", length = 255) private String assessedBy;
    /** APPLICABLE/NOT_APPLICABLE/PARTIAL */
    @Column(length = 20) private String applicability;
    @Column(name = "impact_score") private Integer impactScore;
    @Column(name = "impact_summary_en", columnDefinition = "NVARCHAR(MAX)") private String impactSummaryEn;
    @Column(name = "impact_summary_ar", columnDefinition = "NVARCHAR(MAX)") private String impactSummaryAr;

    @Column(name = "implementation_owner", length = 255) private String implementationOwner;
    @Column(name = "implementation_started_at") private LocalDateTime implementationStartedAt;
    @Column(name = "implementation_target_date") private LocalDate implementationTargetDate;
    @Column(name = "implementation_completed_at") private LocalDateTime implementationCompletedAt;

    @Column(name = "linked_controls", columnDefinition = "NVARCHAR(MAX)") private String linkedControls;
    @Column(name = "linked_capa_ids", columnDefinition = "NVARCHAR(MAX)") private String linkedCapaIds;
    @Column(name = "linked_evidence", columnDefinition = "NVARCHAR(MAX)") private String linkedEvidence;

    @Column(name = "deprioritisation_reason_en", columnDefinition = "NVARCHAR(MAX)") private String deprioritisationReasonEn;

    /** F2-275 (V389) - house rule 7. Nullable and NOT @NotBlank on purpose: bean validation fires on UPDATE too, so requiring Arabic would make every record predating V389 uneditable, and nothing can invent Arabic governance text (F2-30's reason). */
    @Column(name = "deprioritisation_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String deprioritisationReasonAr;
    @Column(name = "closed_at") private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
